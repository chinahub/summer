package cn.jiebaba.summer.ai.embedding.onnx;

import cn.jiebaba.summer.ai.embedding.Embedding;
import cn.jiebaba.summer.ai.embedding.EmbeddingModel;
import cn.jiebaba.summer.ai.embedding.EmbeddingResponse;
import cn.jiebaba.summer.core.onnx.OnnxEngine;
import cn.jiebaba.summer.core.onnx.OnnxEngine.InputTensor;
import cn.jiebaba.summer.core.onnx.OnnxEngine.Output;
import cn.jiebaba.summer.core.onnx.OnnxEngine.Type;
import cn.jiebaba.summer.core.onnx.OnnxException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于 ONNX Runtime 的本地 Embedding 模型实现：加载 BGE-M3 等 XLM-RoBERTa 系 ONNX 模型，
 * 经 {@link BgeTokenizer} 分词后以 int64 token ids 输入模型，读取 FP16 输出做池化与 L2 归一化。
 * 纯 JDK + FFM 调用 onnxruntime，零第三方 Java 依赖，不联网、无 API 成本、低延迟。
 * <p>输入按会话实际输入名动态匹配 input_ids/attention_mask/token_type_ids；输出取第一个
 * （last_hidden_state），支持 CLS 与 mean 两种池化，默认 CLS（BGE 系标准）。
 */
public final class OnnxEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private static final String INPUT_IDS = "input_ids";
    private static final String ATTENTION_MASK = "attention_mask";
    private static final String TOKEN_TYPE_IDS = "token_type_ids";

    private final OnnxEngine engine;
    private final OnnxEngine.Model model;
    private final BgeTokenizer tokenizer;
    private final int maxSeqLen;
    private final boolean meanPooling;
    private final boolean hasTokenTypeIds;
    private volatile int dimensions = -1;

    /**
     * 加载本地 ONNX Embedding 模型。
     *
     * @param libPath onnxruntime 原生库路径（onnxruntime.dll/libonnxruntime.so）
     * @param modelPath BGE-M3 ONNX 模型文件路径
     * @param tokenizerPath tokenizer.json 路径
     * @param maxSeqLen 最大序列长度（超长截断，BGE-M3 支持 8192）
     * @param poolingType 池化方式："cls"（默认）或 "mean"
     */
    public OnnxEmbeddingModel(String libPath, String modelPath, String tokenizerPath,
                              int maxSeqLen, String poolingType) {
        this.engine = OnnxEngine.load(libPath);
        this.model = engine.loadModel(readBytes(modelPath));
        this.tokenizer = new BgeTokenizer(tokenizerPath);
        this.maxSeqLen = maxSeqLen;
        this.meanPooling = "mean".equalsIgnoreCase(poolingType);
        boolean hasTti = false;
        for (String name : model.inputNames()) {
            if (TOKEN_TYPE_IDS.equals(name)) {
                hasTti = true;
                break;
            }
        }
        this.hasTokenTypeIds = hasTti;
    }

    /** 批量向量化：逐条编码推理，结果顺序与输入一致。 */
    @Override
    public EmbeddingResponse embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new EmbeddingResponse(List.of(), "bge-m3");
        }
        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            String text = inputs.get(i);
            float[] vec = embedSingle(text == null ? "" : text);
            embeddings.add(new Embedding(i, vec));
        }
        return new EmbeddingResponse(embeddings, "bge-m3");
    }

    /**
     * 单条文本向量化：分词 -> int64 输入推理 -> FP16 输出读取 -> 池化 -> L2 归一化。
     *
     * @param text 待向量化文本
     * @return 归一化后的浮点向量
     */
    private float[] embedSingle(String text) {
        long[] ids = tokenizer.encode(text, maxSeqLen);
        int seqLen = ids.length;
        long[] attentionMask = new long[seqLen];
        Arrays.fill(attentionMask, 1L);

        List<InputTensor> inputs = new ArrayList<>(3);
        inputs.add(new InputTensor(INPUT_IDS, ids, new long[]{1, seqLen}, Type.INT64));
        inputs.add(new InputTensor(ATTENTION_MASK, attentionMask, new long[]{1, seqLen}, Type.INT64));
        if (hasTokenTypeIds) {
            long[] tokenTypeIds = new long[seqLen];
            inputs.add(new InputTensor(TOKEN_TYPE_IDS, tokenTypeIds, new long[]{1, seqLen}, Type.INT64));
        }

        Output[] outputs = model.run(inputs.toArray(new InputTensor[0]));
        float[] hidden = outputs[0].data();
        long[] shape = outputs[0].shape();
        int hiddenSize = (int) shape[shape.length - 1];
        float[] pooled = meanPooling
                ? meanPool(hidden, seqLen, hiddenSize, attentionMask)
                : clsPool(hidden, hiddenSize);
        return l2Normalize(pooled);
    }

    /** CLS 池化：取首 token（[CLS]）的向量。 */
    private static float[] clsPool(float[] hidden, int hiddenSize) {
        float[] out = new float[hiddenSize];
        System.arraycopy(hidden, 0, out, 0, hiddenSize);
        return out;
    }

    /** 均值池化：按 attention_mask 加权对所有有效 token 向量求平均。 */
    private static float[] meanPool(float[] hidden, int seqLen, int hiddenSize, long[] attentionMask) {
        float[] out = new float[hiddenSize];
        double count = 0;
        for (int t = 0; t < seqLen; t++) {
            if (attentionMask[t] == 0) {
                continue;
            }
            int base = t * hiddenSize;
            for (int d = 0; d < hiddenSize; d++) {
                out[d] += hidden[base + d];
            }
            count++;
        }
        if (count > 0) {
            for (int d = 0; d < hiddenSize; d++) {
                out[d] /= count;
            }
        }
        return out;
    }

    /** L2 归一化：向量除以自身 L2 范数，零向量原样返回。 */
    private static float[] l2Normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        }
        return vec;
    }

    /** 向量维度：惰性探测（编码一个短文本取输出维度并缓存）。 */
    @Override
    public int dimensions() {
        if (dimensions > 0) {
            return dimensions;
        }
        dimensions = embedSingle("dim").length;
        return dimensions;
    }

    /** 读取文件全部字节。 */
    private static byte[] readBytes(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (Exception e) {
            throw new OnnxException("读取模型文件失败：" + path, e);
        }
    }

    @Override
    public void close() {
        try {
            model.close();
        } catch (Exception ignored) {
            // 关闭异常忽略，不阻断
        }
        try {
            engine.close();
        } catch (Exception ignored) {
            // 关闭异常忽略，不阻断
        }
    }
}
