package cn.jiebaba.summer.ai.embedding.onnx;

import cn.jiebaba.summer.core.onnx.OnnxException;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONArray;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XLM-RoBERTa BPE 分词器：解析 HuggingFace tokenizer.json，实现 Metaspace 预分词 + BPE 合并 +
 * Roberta 后处理（加 bos/eos），供 BGE-M3 等 XLM-RoBERTa 系 Embedding 模型使用。纯 JDK 实现，零第三方依赖。
 * <p>分词流程：文本经 Metaspace 预分词（空格转 ▁、词前缀 ▁）得到 pre-token，每个 pre-token 走 BPE
 * （按 merges 优先级合并相邻符号），最终查 vocab 转 id，首尾加 bos/eos。
 */
public final class BgeTokenizer {

    /** Metaspace 替换符（▁），表示词首空格。 */
    private static final String META = "\u2581";
    private static final String BOS = "<s>";
    private static final String EOS = "</s>";
    private static final String UNK = "<unk>";
    private static final String PAD = "<pad>";

    private final Map<String, Integer> vocab = new HashMap<>();
    private final Map<String, Integer> mergeRanks = new HashMap<>();
    private final int bosId;
    private final int eosId;
    private final int unkId;
    private final int padId;

    /** 从 tokenizer.json 文件加载词表、合并规则与特殊 token id。 */
    public BgeTokenizer(String tokenizerPath) {
        String json = readAll(tokenizerPath);
        JSONObject root = JsonUtil.parseObj(json);
        JSONObject model = (JSONObject) root.get("model");
        JSONObject vocabObj = (JSONObject) model.get("vocab");
        for (Map.Entry<String, Object> e : vocabObj.entrySet()) {
            vocab.put(e.getKey(), ((Number) e.getValue()).intValue());
        }
        JSONArray mergesArr = (JSONArray) model.get("merges");
        for (int i = 0; i < mergesArr.size(); i++) {
            mergeRanks.put((String) mergesArr.get(i), i);
        }
        this.bosId = vocab.getOrDefault(BOS, 0);
        this.eosId = vocab.getOrDefault(EOS, 2);
        this.unkId = vocab.getOrDefault(UNK, 3);
        this.padId = vocab.getOrDefault(PAD, 1);
    }

    /**
     * 编码文本为 token id 序列，含 bos/eos，超长按 maxLen 截断。
     *
     * @param text 待编码文本
     * @param maxLen 最大长度（含 bos/eos），超长截断
     * @return token id 数组（int64 语义）
     */
    public long[] encode(String text, int maxLen) {
        List<String> preTokens = metaspaceSplit(text);
        List<Integer> ids = new ArrayList<>();
        ids.add(bosId);
        for (String pt : preTokens) {
            ids.addAll(bpe(pt));
        }
        ids.add(eosId);
        int len = Math.min(ids.size(), maxLen);
        long[] out = new long[len];
        for (int i = 0; i < len; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /**
     * Metaspace 预分词：空格作为词分隔，每个词前缀 ▁，输出形如 ["▁Hello","▁world"] 的 pre-token 列表。
     * 连续空格产生空词（跳过），开头自动补 ▁ 前缀。
     *
     * @param text 原始文本
     * @return pre-token 列表，每项以 ▁ 开头
     */
    private List<String> metaspaceSplit(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder(META);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                if (cur.length() > META.length()) {
                    tokens.add(cur.toString());
                }
                cur = new StringBuilder(META);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > META.length()) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    /**
     * BPE 合并：将 pre-token 拆为字符后按 merges 优先级（rank 越小越先）反复合并相邻符号，
     * 最终查 vocab 转 id；整个 token 或单符号不在 vocab 时用 unk 兜底。
     *
     * @param token 单个 pre-token（以 ▁ 开头）
     * @return token id 列表
     */
    private List<Integer> bpe(String token) {
        Integer direct = vocab.get(token);
        if (direct != null) {
            return List.of(direct);
        }
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < token.length(); i++) {
            symbols.add(String.valueOf(token.charAt(i)));
        }
        if (symbols.size() >= 2) {
            mergeSymbols(symbols);
        }
        List<Integer> ids = new ArrayList<>(symbols.size());
        for (String s : symbols) {
            Integer id = vocab.get(s);
            ids.add(id != null ? id : unkId);
        }
        return ids;
    }

    /** 反复查找 rank 最小的相邻符号对并合并，直到无可合并对。 */
    private void mergeSymbols(List<String> symbols) {
        while (symbols.size() >= 2) {
            int bestRank = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < symbols.size() - 1; i++) {
                String pair = symbols.get(i) + " " + symbols.get(i + 1);
                Integer rank = mergeRanks.get(pair);
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                break;
            }
            symbols.set(bestIdx, symbols.get(bestIdx) + symbols.get(bestIdx + 1));
            symbols.remove(bestIdx + 1);
        }
    }

    public int bosId() {
        return bosId;
    }

    public int eosId() {
        return eosId;
    }

    public int padId() {
        return padId;
    }

    public int vocabSize() {
        return vocab.size();
    }

    /** 读取文件全部内容为 UTF-8 字符串。 */
    private static String readAll(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new OnnxException("读取 tokenizer.json 失败：" + path, e);
        }
    }
}
