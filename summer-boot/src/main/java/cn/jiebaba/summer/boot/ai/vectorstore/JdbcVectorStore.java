package cn.jiebaba.summer.boot.ai.vectorstore;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.embedding.Embedding;
import cn.jiebaba.summer.ai.embedding.EmbeddingModel;
import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;
import cn.jiebaba.summer.ai.vectorstore.SearchRequest;
import cn.jiebaba.summer.ai.vectorstore.VectorStore;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.data.support.JdbcValue;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 基于 PostgreSQL + pgvector 的持久化向量库，复用 summer-data 的 {@link SqlExecutor} 执行 SQL
 * （连接/事务/参数绑定/日志统一由其管理），向量经 {@link VectorTypeHandler} 与 pgvector 文本协议互转。
 * 语义与 {@link cn.jiebaba.summer.ai.vectorstore.InMemoryVectorStore} 一致：余弦相似度
 * （1 - cosine_distance）排序、阈值过滤、topK 截断。适合生产级 RAG：语料持久化、重启不丢失、可跨进程共享。
 *
 * <p>表结构：{@code id TEXT PK, content TEXT, metadata TEXT(JSON), embedding vector(dim)}，
 * 默认创建 HNSW 余弦索引。维度 dim 由配置指定，未指定时惰性探测 EmbeddingModel.dimensions()。
 * 写入为多值 upsert（{@code ON CONFLICT (id) DO UPDATE}），检索用 {@code 1 - (embedding <=> ?::vector)} 算相似度。
 */
public class JdbcVectorStore implements VectorStore {

    /** 合法表名/索引名标识符，防 SQL 注入。 */
    private static final Pattern IDENT = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final SqlExecutor sqlExecutor;
    private final EmbeddingModel embeddingModel;
    private final String table;
    private final int configuredDimensions;
    private final boolean createExtension;
    private final boolean createIndex;
    private volatile boolean initialized = false;

    public JdbcVectorStore(SqlExecutor sqlExecutor, EmbeddingModel embeddingModel, String table,
                           int configuredDimensions, boolean createExtension, boolean createIndex) {
        if (sqlExecutor == null) {
            throw new IllegalArgumentException("sqlExecutor 不能为空");
        }
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel 不能为空");
        }
        if (table == null || !IDENT.matcher(table).matches()) {
            throw new IllegalArgumentException("非法表名: " + table);
        }
        this.sqlExecutor = sqlExecutor;
        this.embeddingModel = embeddingModel;
        this.table = table;
        this.configuredDimensions = configuredDimensions;
        this.createExtension = createExtension;
        this.createIndex = createIndex;
    }

    /** 批量写入：为每个文档向量化并多值 upsert（按 id 冲突更新），一次 SQL 完成，返回 id 列表。 */
    @Override
    public List<String> add(List<Document> documents) {
        ensureInitialized();
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>(documents.size());
        for (Document d : documents) {
            texts.add(d.content() == null ? "" : d.content());
        }
        List<float[]> vectors = new ArrayList<>(documents.size());
        for (Embedding e : embeddingModel.embed(texts).embeddings()) {
            vectors.add(e.vector());
        }
        List<String> ids = new ArrayList<>(documents.size());
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(table).append(" (id, content, metadata, embedding) VALUES ");
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            String id = d.id() != null && !d.id().isBlank() ? d.id() : UUID.randomUUID().toString();
            ids.add(id);
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?::vector)");
            params.add(id);
            params.add(d.content());
            params.add(JsonUtil.toJsonStr(d.metadata()));
            params.add(new JdbcValue(vectors.get(i), VectorTypeHandler.INSTANCE));
        }
        sql.append(" ON CONFLICT (id) DO UPDATE SET content=EXCLUDED.content,")
                .append(" metadata=EXCLUDED.metadata, embedding=EXCLUDED.embedding");
        sqlExecutor.update(new SqlBuilder.Sql(sql.toString(), params));
        return ids;
    }

    /** 按 id 批量删除（IN 列表一次删除）。 */
    @Override
    public void delete(List<String> ids) {
        ensureInitialized();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table).append(" WHERE id IN (");
        List<Object> params = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            params.add(ids.get(i));
        }
        sql.append(')');
        sqlExecutor.update(new SqlBuilder.Sql(sql.toString(), params));
    }

    /** 检索：向量化查询，按余弦相似度（1 - cosine_distance）过滤阈值并取 topK，经 RowMapper 映射。 */
    @Override
    public List<RetrievalResult> similaritySearch(SearchRequest request) {
        ensureInitialized();
        if (request == null || request.query() == null) {
            return List.of();
        }
        float[] queryVec = embeddingModel.embed(request.query()).embeddings().get(0).vector();
        Object vecParam = new JdbcValue(queryVec, VectorTypeHandler.INSTANCE);
        int topK = request.topK();
        StringBuilder sql = new StringBuilder("SELECT id, content, metadata, 1 - (embedding <=> ?::vector) AS score FROM ")
                .append(table);
        List<Object> params = new ArrayList<>();
        params.add(vecParam);
        List<String> conditions = new ArrayList<>();
        if (request.similarityThreshold() > 0.0) {
            conditions.add("1 - (embedding <=> ?::vector) >= ?");
            params.add(vecParam);
            params.add(request.similarityThreshold());
        }
        if (!request.filter().isEmpty()) {
            conditions.add("metadata::jsonb @> ?::jsonb");
            params.add(JsonUtil.toJsonStr(request.filter()));
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY score DESC");
        if (topK > 0) {
            sql.append(" LIMIT ").append(topK);
        }
        return sqlExecutor.query(new SqlBuilder.Sql(sql.toString(), params),
                (rs, n) -> new RetrievalResult(
                        new Document(rs.getString("id"), rs.getString("content"),
                                parseMetadata(rs.getString("metadata"))),
                        rs.getDouble("score")));
    }

    /** 首次调用时建表与索引：维度优先取配置，否则惰性探测 EmbeddingModel.dimensions()。 */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            int dim = configuredDimensions > 0 ? configuredDimensions : embeddingModel.dimensions();
            if (dim <= 0) {
                throw new AiException("无法确定向量维度：请配置 summer.ai.vectorstore.dimensions 或确保 embedding 模型可探测维度");
            }
            initSchema(dim);
            initialized = true;
        }
    }

    /** 建扩展（忽略权限错误）、建表、建 HNSW 余弦索引，均经 SqlExecutor 执行。 */
    private void initSchema(int dim) {
        if (createExtension) {
            try {
                sqlExecutor.update(new SqlBuilder.Sql("CREATE EXTENSION IF NOT EXISTS vector", List.of()));
            } catch (Exception ignored) {
                // 扩展可能已由 DBA 创建，当前角色无 CREATE EXTENSION 权限属正常，忽略后继续建表
            }
        }
        sqlExecutor.update(new SqlBuilder.Sql("CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id TEXT PRIMARY KEY,"
                + "content TEXT,"
                + "metadata TEXT,"
                + "embedding vector(" + dim + "))", List.of()));
        if (createIndex) {
            sqlExecutor.update(new SqlBuilder.Sql("CREATE INDEX IF NOT EXISTS " + table + "_embedding_idx ON " + table
                    + " USING hnsw (embedding vector_cosine_ops)", List.of()));
        }
    }

    /** 解析 metadata JSON 串为 Map；空或异常返回空 Map。 */
    private static Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = JsonUtil.parse(json);
            if (parsed instanceof Map<?, ?> m) {
                Map<String, Object> out = new LinkedHashMap<>(m.size());
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
        } catch (Exception ignored) {
            // metadata 解析失败不阻断检索，回退空元数据
        }
        return Map.of();
    }
}
