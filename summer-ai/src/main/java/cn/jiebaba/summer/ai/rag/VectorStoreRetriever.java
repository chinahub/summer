package cn.jiebaba.summer.ai.rag;

import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;
import cn.jiebaba.summer.ai.vectorstore.SearchRequest;
import cn.jiebaba.summer.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * 基于向量库的检索器：将查询转为 SearchRequest 交给 VectorStore 做语义相似度检索。
 */
public class VectorStoreRetriever implements Retriever {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public VectorStoreRetriever(VectorStore vectorStore, int topK, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public VectorStoreRetriever(VectorStore vectorStore, int topK) {
        this(vectorStore, topK, 0.0);
    }

    public VectorStoreRetriever(VectorStore vectorStore) {
        this(vectorStore, 4, 0.0);
    }

    @Override
    public List<RetrievalResult> retrieve(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
    }
}
