package cn.jiebaba.summer.office.ocr;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本识别：对每个文本裁剪图做 CRNN+CTC 识别。移植自 rapidocr TextRecognizer + CTCLabelDecode。
 * <p>预处理：按批次最大宽高比计算目标宽，缩放到固定高 imgH、归一化后右侧零填充；
 * 推理输出 [N, seq, C]，CTC 解码（去重复、去 blank）得到文本与平均置信度。
 */
final class TextRecognizer {

    /** 单行识别结果。 */
    static final class Rec {
        final String text;
        final float score;

        Rec(String text, float score) {
            this.text = text;
            this.score = score;
        }
    }

    private final OnnxEngine.Model model;
    private final int imgC;
    private final int imgH;
    private final int imgW;
    private final int batchNum;
    private final List<String> characters;

    TextRecognizer(OnnxEngine.Model model, OcrConfig cfg, List<String> dictLines) {
        this.model = model;
        int[] shape = cfg.recImageShape();
        this.imgC = shape[0];
        this.imgH = shape[1];
        this.imgW = shape[2];
        this.batchNum = cfg.recBatchNum();
        List<String> chars = new ArrayList<>();
        chars.add("blank");
        chars.addAll(dictLines);
        chars.add(" ");
        this.characters = chars;
    }

    /**
     * 逐批识别裁剪图，返回与输入顺序一致的识别结果。
     *
     * @param crops 方向校正后的文本裁剪图
     * @return 每行的文本与置信度
     */
    List<Rec> recognize(List<ImageUtil.Img> crops) {
        List<Rec> out = new ArrayList<>();
        int n = crops.size();
        for (int beg = 0; beg < n; beg += batchNum) {
            int end = Math.min(n, beg + batchNum);
            int batch = end - beg;
            float maxRatio = (float) imgW / imgH;
            for (int i = beg; i < end; i++) {
                ImageUtil.Img c = crops.get(i);
                maxRatio = Math.max(maxRatio, c.width / (float) c.height);
            }
            int imgWidth = (int) (imgH * maxRatio);
            float[] input = new float[batch * imgC * imgH * imgWidth];
            for (int i = 0; i < batch; i++) {
                resizeNormInto(crops.get(beg + i), input, i * imgC * imgH * imgWidth, imgWidth);
            }
            OnnxEngine.Output o = model.run(input, new long[]{batch, imgC, imgH, imgWidth});
            float[] data = o.data();
            long[] shape = o.shape();
            int seq = (int) shape[1];
            int cls = (int) shape[2];
            for (int i = 0; i < batch; i++) {
                out.add(decode(data, i, seq, cls));
            }
        }
        return out;
    }

    /** 缩放、归一化并右侧零填充到 [imgC,imgH,imgWidth]，写入 target 起 offset。 */
    private void resizeNormInto(ImageUtil.Img crop, float[] target, int offset, int imgWidth) {
        float ratio = crop.width / (float) crop.height;
        int resizedW = (int) Math.ceil(imgH * ratio);
        if (resizedW > imgWidth) {
            resizedW = imgWidth;
        }
        ImageUtil.Img resized = ImageUtil.resize(crop, resizedW, imgH);
        int plane = imgH * imgWidth;
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < resizedW; x++) {
                int c = resized.rgb[y * resizedW + x];
                target[offset + y * imgWidth + x] = ((c >> 16 & 0xFF) / 255f - 0.5f) / 0.5f;
                target[offset + plane + y * imgWidth + x] = ((c >> 8 & 0xFF) / 255f - 0.5f) / 0.5f;
                target[offset + plane * 2 + y * imgWidth + x] = ((c & 0xFF) / 255f - 0.5f) / 0.5f;
            }
        }
    }

    /**
     * CTC 解码单行：取每时间步 argmax，去重复、去 blank（索引 0），映射到字符，平均置信度。
     *
     * @param data 推理输出扁平数据 [N][seq][cls]
     * @param n    行索引
     * @param seq  时间步数
     * @param cls  类别数
     */
    private Rec decode(float[] data, int n, int seq, int cls) {
        StringBuilder sb = new StringBuilder();
        double sum = 0;
        int count = 0;
        int prev = -1;
        for (int t = 0; t < seq; t++) {
            int base = (n * seq + t) * cls;
            int best = 0;
            float bestProb = data[base];
            for (int c = 1; c < cls; c++) {
                if (data[base + c] > bestProb) {
                    bestProb = data[base + c];
                    best = c;
                }
            }
            if (best != 0 && best != prev && best < characters.size()) {
                sb.append(characters.get(best));
                sum += bestProb;
                count++;
            }
            prev = best;
        }
        float score = count > 0 ? (float) (sum / count) : 0f;
        return new Rec(sb.toString(), score);
    }
}
