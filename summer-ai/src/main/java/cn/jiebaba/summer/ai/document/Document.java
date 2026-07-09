package cn.jiebaba.summer.ai.document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档：向量库与 RAG 的基本单元，含唯一 id、文本内容与可选元数据。
 * 元数据可记录来源、标题、分块位置等，便于检索后溯源。
 */
public record Document(String id, String content, Map<String, Object> metadata) {

    public Document {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Document(String id, String content) {
        this(id, content, Map.of());
    }

    /** 仅以内容构造，id 留空由调用方（如向量库）自动生成。 */
    public static Document of(String content) {
        return new Document(null, content, Map.of());
    }

    public static Document of(String id, String content) {
        return new Document(id, content, Map.of());
    }

    /** 便捷构造器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 链式构造器，便于附加元数据。 */
    public static class Builder {
        private String id;
        private String content;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Document build() {
            return new Document(id, content, metadata);
        }
    }
}
