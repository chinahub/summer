package cn.jiebaba.summer.ai.rag;

import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;

import java.util.List;

/**
 * 检索抽象：按查询文本返回相关文档片段，供 RAG 增强使用。
 * 实现可基于向量库、关键词索引或混合检索。
 */
public interface Retriever {

    List<RetrievalResult> retrieve(String query);
}
