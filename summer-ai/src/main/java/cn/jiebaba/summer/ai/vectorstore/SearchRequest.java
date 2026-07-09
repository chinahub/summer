package cn.jiebaba.summer.ai.vectorstore;

/**
 * 向量检索请求：查询文本、返回条数 topK 与相似度阈值。
 * 阈值用于过滤低相关结果，默认 0 表示不限制。
 */
public record SearchRequest(String query, int topK, double similarityThreshold) {

    public SearchRequest(String query) {
        this(query, 4, 0.0);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 链式构造器。 */
    public static class Builder {
        private String query;
        private int topK = 4;
        private double similarityThreshold = 0.0;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder similarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(query, topK, similarityThreshold);
        }
    }
}
