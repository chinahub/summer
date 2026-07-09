package cn.jiebaba.summer.ai.vectorstore;

import cn.jiebaba.summer.ai.document.Document;

import java.util.List;

/**
 * 向量库抽象：存储带向量的文档并按语义相似度检索。
 * 具体实现可为内存、外部向量数据库等。
 */
public interface VectorStore {

    /** 写入文档列表（自动向量化），返回生成的 id 列表。 */
    List<String> add(List<Document> documents);

    /** 单文档写入便捷方法。 */
    default String add(Document document) {
        return add(List.of(document)).get(0);
    }

    /** 按 id 删除文档。 */
    void delete(List<String> ids);

    /** 按语义相似度检索最相关的文档。 */
    List<RetrievalResult> similaritySearch(SearchRequest request);
}
