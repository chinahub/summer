package cn.jiebaba.summer.ai.vectorstore;

/**
 * 向量相似度工具，提供余弦相似度计算。
 * 与具体向量库实现解耦，供检索排序复用。
 */
public final class SimilarityUtil {

    private SimilarityUtil() {
    }

    /** 计算两个向量的余弦相似度，取值 [-1,1]；任一为零向量返回 0。 */
    public static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0f;
        }
        float dot = 0f;
        float na = 0f;
        float nb = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt((double) na) * Math.sqrt((double) nb);
        return denom == 0 ? 0f : (float) (dot / denom);
    }
}
