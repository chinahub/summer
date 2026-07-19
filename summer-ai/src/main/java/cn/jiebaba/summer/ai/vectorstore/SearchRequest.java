package cn.jiebaba.summer.ai.vectorstore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向量检索请求：查询文本、返回条数 topK、相似度阈值与元数据过滤条件。
 * 阈值用于过滤低相关结果，默认 0 表示不限制；filter 为元数据键值对等值匹配，
 * 仅返回 metadata 包含全部 filter 键值对的文档（pgvector 走 JSONB @> 包含语义，内存走谓词逐条比对）。
 */
public record SearchRequest(String query, int topK, double similarityThreshold,
                            Map<String, Object> filter) {

    public SearchRequest {
        filter = filter == null ? Map.of() : Map.copyOf(filter);
    }

    public SearchRequest(String query, int topK, double similarityThreshold) {
        this(query, topK, similarityThreshold, null);
    }

    public SearchRequest(String query) {
        this(query, 4, 0.0, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 链式构造器。 */
    public static class Builder {
        private String query;
        private int topK = 4;
        private double similarityThreshold = 0.0;
        private Map<String, Object> filter = new LinkedHashMap<>();

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

        /** 设置元数据过滤的完整键值映射（替换已有过滤条件）。 */
        public Builder filter(Map<String, Object> filter) {
            this.filter = filter == null ? new LinkedHashMap<>() : new LinkedHashMap<>(filter);
            return this;
        }

        /** 追加单个元数据等值过滤条件（如 source=web），可链式叠加多个。 */
        public Builder filter(String key, Object value) {
            this.filter.put(key, value);
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(query, topK, similarityThreshold, filter);
        }
    }
}
