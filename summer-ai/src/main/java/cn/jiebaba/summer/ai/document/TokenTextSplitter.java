package cn.jiebaba.summer.ai.document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 token 估算的文本切分器：以近似 token 为单位成块，相邻块按 overlap 重叠，
 * 兼顾中英文（连续英文/数字视为一词，单个 CJK 等字符视为一词）。
 * 切块直接取原始文本子串，保留原始空白与标点，不因分词插入多余空格。
 * 不依赖第三方分词器，token 估算对检索分块足够精确。
 */
public class TokenTextSplitter implements TextSplitter {

    /** 匹配连续 ASCII 词（字母/数字/下划线）或单个非空白字符，CJK 等按单字切词。 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\w+|[^\\s]");

    private final int chunkSize;
    private final int overlap;

    public TokenTextSplitter(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须为正");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 须在 [0, chunkSize) 区间");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /** 默认 chunkSize=500 token、overlap=50 token 的便捷构造。 */
    public TokenTextSplitter() {
        this(500, 50);
    }

    /** 按近似 token 切分文本为带重叠的块，每块为原始文本子串，保留空白与标点。 */
    @Override
    public List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<int[]> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(start + chunkSize, tokens.size());
            int[] first = tokens.get(start);
            int[] last = tokens.get(end - 1);
            chunks.add(text.substring(first[0], last[1]));
            if (end >= tokens.size()) {
                break;
            }
        }
        return chunks;
    }

    /** 将文本切为近似 token 的 [start, end) 偏移列表：连续英文/数字一词，其余按单字。 */
    private List<int[]> tokenize(String text) {
        Matcher m = TOKEN_PATTERN.matcher(text);
        List<int[]> tokens = new ArrayList<>();
        while (m.find()) {
            tokens.add(new int[]{m.start(), m.end()});
        }
        return tokens;
    }
}
