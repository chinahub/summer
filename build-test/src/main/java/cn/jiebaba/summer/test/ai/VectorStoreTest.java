package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.vectorstore.InMemoryVectorStore;
import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;
import cn.jiebaba.summer.ai.vectorstore.SearchRequest;
import cn.jiebaba.summer.ai.vectorstore.SimilarityUtil;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.util.List;

/** 内存向量库与相似度工具的单元测试。 */
public class VectorStoreTest {

    @Test
    public void cosineSimilarityBasics() {
        float[] a = {1f, 0f, 0f};
        Assert.assertTrue(Math.abs(SimilarityUtil.cosine(a, a) - 1.0) < 1e-6);
        float[] b = {0f, 1f, 0f};
        Assert.assertTrue(Math.abs(SimilarityUtil.cosine(a, b)) < 1e-6);
        Assert.assertEquals(0f, SimilarityUtil.cosine(a, new float[0]));
    }

    @Test
    public void addAndSearchRanksBySimilarity() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana"),
                Document.of("cat dog"),
                Document.of("apple cat")));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder().query("apple").topK(3).build());
        Assert.assertFalse(results.isEmpty());
        Assert.assertTrue(results.get(0).document().content().contains("apple"));
        Assert.assertEquals(3, store.size());
    }

    @Test
    public void topKLimitsResults() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana"),
                Document.of("cat dog"),
                Document.of("apple cat")));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder().query("apple").topK(1).build());
        Assert.assertEquals(1, results.size());
    }

    @Test
    public void thresholdFiltersResults() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana"),
                Document.of("cat dog")));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder()
                .query("apple").topK(5).similarityThreshold(0.99).build());
        Assert.assertTrue(results.isEmpty(), "高阈值应过滤掉所有结果");
    }

    @Test
    public void deleteRemovesDocument() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        java.util.List<String> ids = store.add(List.of(Document.of("apple banana")));
        Assert.assertEquals(1, store.size());
        store.delete(ids);
        Assert.assertEquals(0, store.size());
    }
}
