package cn.jiebaba.summer.ai.document;

import java.util.List;

/**
 * 文本切分抽象：将长文档拆为更小的分块，便于向量化与检索。
 * 同时提供按字符串切分与按文档切分两种入口。
 */
public interface TextSplitter {

    /** 将纯文本切分为多段。 */
    List<String> split(String text);

    /** 将单个文档切分为多个文档，保留原始元数据并追加分块序号。 */
    default List<Document> split(Document document) {
        List<String> chunks = split(document.content());
        java.util.List<Document> result = new java.util.ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>(document.metadata());
            meta.put("chunkIndex", i);
            meta.put("chunkCount", chunks.size());
            result.add(new Document(document.id(), chunks.get(i), meta));
        }
        return result;
    }
}
