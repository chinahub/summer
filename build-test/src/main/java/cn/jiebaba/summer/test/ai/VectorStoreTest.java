package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.vectorstore.InMemoryVectorStore;
import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;
import cn.jiebaba.summer.ai.vectorstore.SearchRequest;
import cn.jiebaba.summer.ai.vectorstore.SimilarityUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 内存向量库与相似度工具的单元测试。 */
public class VectorStoreTest {

    @Test
    public void cosineSimilarityBasics() {
        float[] a = {1f, 0f, 0f};
        Assertions.assertTrue(Math.abs(SimilarityUtil.cosine(a, a) - 1.0) < 1e-6);
        float[] b = {0f, 1f, 0f};
        Assertions.assertTrue(Math.abs(SimilarityUtil.cosine(a, b)) < 1e-6);
        Assertions.assertEquals(0f, SimilarityUtil.cosine(a, new float[0]));
    }

    @Test
    public void addAndSearchRanksBySimilarity() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana"),
                Document.of("cat dog"),
                Document.of("apple cat")));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder().query("apple").topK(3).build());
        Assertions.assertFalse(results.isEmpty());
        Assertions.assertTrue(results.get(0).document().content().contains("apple"));
        Assertions.assertEquals(3, store.size());
    }

    @Test
    public void topKLimitsResults() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana"),
                Document.of("cat dog"),
                Document.of("apple cat")));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder().query("apple").topK(1).build());
        Assertions.assertEquals(1, results.size());
    }

    @Test
    public void thresholdFiltersResults() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana"),
                Document.of("cat dog")));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder()
                .query("apple").topK(5).similarityThreshold(0.99).build());
        Assertions.assertTrue(results.isEmpty(), "高阈值应过滤掉所有结果");
    }

    @Test
    public void deleteRemovesDocument() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        java.util.List<String> ids = store.add(List.of(Document.of("apple banana")));
        Assertions.assertEquals(1, store.size());
        store.delete(ids);
        Assertions.assertEquals(0, store.size());
    }

    @Test
    public void metadataFilterRestrictsResults() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.builder().content("apple banana").metadata("source", "web").build(),
                Document.builder().content("apple cat").metadata("source", "db").build(),
                Document.builder().content("apple dog").metadata("source", "web").build()));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder()
                .query("apple").topK(10).filter("source", "web").build());
        Assertions.assertEquals(2, results.size(), "应仅命中 source=web 的 2 条文档");
        Assertions.assertTrue(results.stream().allMatch(r -> "web".equals(r.document().metadata().get("source"))),
                "过滤后结果应全部 source=web");
    }

    @Test
    public void metadataFilterMultipleKeys() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.builder().content("apple banana").metadata("source", "web").metadata("lang", "en").build(),
                Document.builder().content("apple cat").metadata("source", "web").metadata("lang", "zh").build(),
                Document.builder().content("apple dog").metadata("source", "db").metadata("lang", "en").build()));
        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder()
                .query("apple").topK(10)
                .filter("source", "web").filter("lang", "en")
                .build());
        Assertions.assertEquals(1, results.size(), "应仅命中 source=web 且 lang=en 的 1 条");
        Assertions.assertEquals("apple banana", results.get(0).document().content());
    }
}
