package cn.jiebaba.summer.ai.embedding.onnx;

import cn.jiebaba.summer.core.onnx.OnnxException;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONArray;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * XLM-RoBERTa 系分词器：解析 HuggingFace tokenizer.json，实现 Metaspace 预分词 +
 * BPE/Unigram 两种模型 + Roberta 后处理（加 bos/eos），供 BGE-M3 等 XLM-RoBERTa 系
 * Embedding 模型使用。纯 JDK 实现，零第三方依赖。
 * <p>分词流程：文本经 Metaspace 预分词（空格转 ▁、词前缀 ▁）得到 pre-token，每个 pre-token
 * 按模型类型走 BPE（按 merges 优先级合并相邻符号）或 Unigram（Viterbi 最优切分，
 * 未知字符按 byte_fallback 回退为 {@code <0xXX>} 字节片，无字节片时用 unk 兜底），
 * 最终查 vocab 转 id，首尾加 bos/eos。</p>
 * <p>注意：BGE-M3 / XLM-RoBERTa 官方 tokenizer.json 均为 Unigram 格式（vocab 为
 * [token, score] 列表、无 merges），BPE 格式仅兼容部分社区转换版。</p>
 */
public final class BgeTokenizer {

    /** Metaspace 替换符（▁），表示词首空格。 */
    private static final String META = "▁";
    private static final String BOS = "<s>";
    private static final String EOS = "</s>";
    private static final String UNK = "<unk>";
    private static final String PAD = "<pad>";

    /** 仅作为文本切分候选排除的特殊 token（BOS/EOS/UNK/PAD 不参与普通文本匹配）。 */
    private static final Set<String> SPECIALS = Set.of(BOS, EOS, UNK, PAD);

    /** Unigram 未知字符惩罚分：在最低分基础上再罚 10（与 HF tokenizers 的 kUnkPenalty 一致）。 */
    private static final double UNK_PENALTY = 10.0;

    private final Map<String, Integer> vocab = new HashMap<>();
    private final Map<String, Integer> mergeRanks = new HashMap<>();
    /** Unigram 模式：按 id 索引的分片得分（对数概率，≤0）。 */
    private double[] scores = new double[0];
    /** Unigram 模式：未知字符是否回退为 <0xXX> 字节片。 */
    private boolean byteFallback;
    /** Unigram 模式：vocab 全部分片的所有前缀（用于匹配剪枝）。 */
    private final Set<String> prefixes = new HashSet<>();
    /** Unigram 模式：全体分片得分的最小值（计算 unk 惩罚分用）。 */
    private double minScore;
    private final boolean unigram;
    private final int bosId;
    private final int eosId;
    private final int unkId;
    private final int padId;

    /** 从 tokenizer.json 文件加载词表与模型参数（按 model.type 自动识别 BPE/Unigram）。 */
    public BgeTokenizer(String tokenizerPath) {
        String json = readAll(tokenizerPath);
        JSONObject root = JsonUtil.parseObj(json);
        JSONObject model = (JSONObject) root.get("model");
        Object type = model == null ? null : model.get("type");
        if ("Unigram".equals(type)) {
            loadUnigram(model);
            this.unigram = true;
        } else {
            loadBpe(model);
            this.unigram = false;
        }
        this.bosId = vocab.getOrDefault(BOS, 0);
        this.eosId = vocab.getOrDefault(EOS, 2);
        this.unkId = vocab.getOrDefault(UNK, 3);
        this.padId = vocab.getOrDefault(PAD, 1);
    }

    /** 加载 BPE 模型：vocab 为 token→id 对象，merges 为按优先级排序的合并规则。 */
    private void loadBpe(JSONObject model) {
        JSONObject vocabObj = (JSONObject) model.get("vocab");
        for (Map.Entry<String, Object> e : vocabObj.entrySet()) {
            vocab.put(e.getKey(), ((Number) e.getValue()).intValue());
        }
        JSONArray mergesArr = (JSONArray) model.get("merges");
        for (int i = 0; i < mergesArr.size(); i++) {
            mergeRanks.put((String) mergesArr.get(i), i);
        }
    }

    /** 加载 Unigram 模型：vocab 为 [token, score] 列表（下标即 id），并构建前缀剪枝集。 */
    private void loadUnigram(JSONObject model) {
        JSONArray vocabArr = (JSONArray) model.get("vocab");
        this.scores = new double[vocabArr.size()];
        this.minScore = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < vocabArr.size(); i++) {
            JSONArray entry = (JSONArray) vocabArr.get(i);
            String piece = (String) entry.get(0);
            vocab.put(piece, i);
            double score = ((Number) entry.get(1)).doubleValue();
            scores[i] = score;
            min = Math.min(min, score);
        }
        this.minScore = min == Double.POSITIVE_INFINITY ? 0 : min;
        this.byteFallback = Boolean.TRUE.equals(model.get("byte_fallback"));
        for (String piece : vocab.keySet()) {
            int[] cps = piece.codePoints().toArray();
            StringBuilder sb = new StringBuilder();
            for (int cp : cps) {
                sb.appendCodePoint(cp);
                prefixes.add(sb.toString());
            }
        }
    }

    /**
     * 编码文本为 token id 序列，含 bos/eos，超长按 maxLen 截断。
     *
     * @param text 待编码文本
     * @param maxLen 最大长度（含 bos/eos），超长截断
     * @return token id 数组（int64 语义）
     */
    public long[] encode(String text, int maxLen) {
        if (unigram) {
            text = normalizeText(text);
        }
        List<String> preTokens = metaspaceSplit(text);
        List<Integer> ids = new ArrayList<>();
        ids.add(bosId);
        for (String pt : preTokens) {
            ids.addAll(unigram ? unigram(pt) : bpe(pt));
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
     * Unigram 归一化：近似 tokenizer.json 中 Sequence[Precompiled, Replace] 的效果。
     * Precompiled 是 sentencepiece 的预编译字符映射（以 NFKC 为主体），这里用 JDK 自带
     * NFKC 近似，并补充控制空白归为空格与连续空格折叠（Replace 规则），
     * 否则全角标点（，！等）与控制字符无法命中词表而降级为 unk。
     */
    private static String normalizeText(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = s.replaceAll("[\\t\\n\\r\\f\\x0B]", " ");
        s = s.replaceAll(" {2,}", " ");
        return s;
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
     * Unigram 最优切分：在 pre-token 的码点序列上做 Viterbi 动态规划，最大化分片得分之和。
     * 普通分片经前缀集剪枝后查表；每个位置另备 byte_fallback 字节片候选（未知字符展开为
     * UTF-8 字节对应的 {@code <0xXX>} 分片），未启用 byte_fallback 时用 unk 惩罚分兜底。
     *
     * @param token 单个 pre-token（以 ▁ 开头）
     * @return token id 列表
     */
    private List<Integer> unigram(String token) {
        Integer direct = vocab.get(token);
        if (direct != null && !SPECIALS.contains(token)) {
            return List.of(direct);
        }
        int[] cps = token.codePoints().toArray();
        int n = cps.length;
        double[] best = new double[n + 1];
        int[] from = new int[n + 1];
        int[][] edgeIds = new int[n + 1][];
        Arrays.fill(best, Double.NEGATIVE_INFINITY);
        Arrays.fill(from, -1);
        best[0] = 0;
        for (int i = 0; i < n; i++) {
            if (best[i] == Double.NEGATIVE_INFINITY) {
                continue;
            }
            // 候选一：词表分片（前缀剪枝，整段递增匹配）
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < n; j++) {
                sb.appendCodePoint(cps[j]);
                String piece = sb.toString();
                Integer id = vocab.get(piece);
                if (id != null && !SPECIALS.contains(piece)) {
                    relax(best, from, edgeIds, i, j + 1, best[i] + scores[id], new int[]{id});
                }
                if (!prefixes.contains(piece)) {
                    break;
                }
            }
            // 候选二：byte_fallback / unk 兜底（单码点消费）
            int[] fallbackIds = byteFallbackIds(cps[i]);
            double fallbackScore;
            if (fallbackIds != null) {
                fallbackScore = best[i];
                for (int id : fallbackIds) {
                    fallbackScore += scores[id];
                }
            } else {
                fallbackIds = new int[]{unkId};
                fallbackScore = best[i] + minScore - UNK_PENALTY;
            }
            relax(best, from, edgeIds, i, i + 1, fallbackScore, fallbackIds);
        }
        // 回溯最优路径
        List<Integer> out = new ArrayList<>();
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        for (int j = n; j > 0; j = from[j]) {
            stack.push(edgeIds[j]);
        }
        while (!stack.isEmpty()) {
            for (int id : stack.pop()) {
                out.add(id);
            }
        }
        return out;
    }

    /** 松弛更新：若经 start→end 的候选边得分更高，则记录该路径。 */
    private static void relax(double[] best, int[] from, int[][] edgeIds,
                              int start, int end, double score, int[] ids) {
        if (score > best[end]) {
            best[end] = score;
            from[end] = start;
            edgeIds[end] = ids;
        }
    }

    /** 求单码点的 byte_fallback 分片 id 序列（UTF-8 每字节对应一个 <0xXX> 分片）；缺字节片返回 null。 */
    private int[] byteFallbackIds(int cp) {
        if (!byteFallback) {
            return null;
        }
        byte[] bytes = new String(new int[]{cp}, 0, 1).getBytes(StandardCharsets.UTF_8);
        int[] ids = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            String piece = String.format("<0x%02X>", bytes[i] & 0xFF);
            Integer id = vocab.get(piece);
            if (id == null) {
                return null;
            }
            ids[i] = id;
        }
        return ids;
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
