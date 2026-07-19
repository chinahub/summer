package cn.jiebaba.summer.ai.vectorstore;

import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.embedding.Embedding;
import cn.jiebaba.summer.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 内存向量库：用 EmbeddingModel 在写入与查询时即时向量化，余弦相似度排序。
 * 线程安全（方法 synchronized），适合中小规模语料与单机演示。
 */
public class InMemoryVectorStore implements VectorStore {

    private final EmbeddingModel embeddingModel;
    private final Map<String, Entry> store = new LinkedHashMap<>();

    public InMemoryVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** 批量写入：为每个文档向量化并分配 id，返回 id 列表。 */
    @Override
    public synchronized List<String> add(List<Document> documents) {
        List<String> texts = new ArrayList<>(documents.size());
        for (Document d : documents) {
            texts.add(d.content() == null ? "" : d.content());
        }
        List<float[]> vectors = new ArrayList<>(documents.size());
        for (Embedding e : embeddingModel.embed(texts).embeddings()) {
            vectors.add(e.vector());
        }
        List<String> ids = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            String id = d.id() != null && !d.id().isBlank() ? d.id() : UUID.randomUUID().toString();
            store.put(id, new Entry(d, vectors.get(i)));
            ids.add(id);
        }
        return ids;
    }

    /** 按 id 批量删除。 */
    @Override
    public synchronized void delete(List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            store.remove(id);
        }
    }

    /** 检索：向量化查询，计算与所有文档的余弦相似度，过滤阈值后取 topK。 */
    @Override
    public synchronized List<RetrievalResult> similaritySearch(SearchRequest request) {
        float[] query = embeddingModel.embed(request.query()).embeddings().get(0).vector();
        Map<String, Object> filter = request.filter();
        List<RetrievalResult> all = new ArrayList<>(store.size());
        for (Entry e : store.values()) {
            if (!matchesFilter(e.document, filter)) {
                continue;
            }
            double score = SimilarityUtil.cosine(query, e.vector);
            if (score >= request.similarityThreshold()) {
                all.add(new RetrievalResult(e.document, score));
            }
        }
        all.sort(Comparator.comparingDouble(RetrievalResult::score).reversed());
        int limit = Math.max(0, request.topK());
        return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
    }

    /** 元数据等值过滤：filter 为空则全部通过；否则要求文档 metadata 包含全部 filter 键值对。 */
    private static boolean matchesFilter(Document document, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> meta = document.metadata();
        for (Map.Entry<String, Object> e : filter.entrySet()) {
            if (!Objects.equals(meta.get(e.getKey()), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** 当前文档数量。 */
    public synchronized int size() {
        return store.size();
    }

    /** 存储条目：文档与向量。 */
    private record Entry(Document document, float[] vector) {
    }
}
