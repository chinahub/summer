package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.boot.ai.vectorstore.JdbcVectorStore;
import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;
import cn.jiebaba.summer.ai.vectorstore.SearchRequest;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * JdbcVectorStore 针对 PostgreSQL + pgvector 的端到端测试。
 * 复用 summer-sample 的 datasource 配置（经 Environment 读取，不硬编码凭据），
 * 用 StubEmbeddingModel 提供确定性向量，避免依赖外部 embedding API。
 * 当 DB 不可达或未启用 pgvector 时，通过 {@link Assumptions#assumeTrue} 跳过而非失败。
 */
public class JdbcVectorStoreTest {

    private static final String TABLE = "summer_ai_vec_test";

    /** 端到端：建表 -> 写入 -> 相似度检索 -> 删除 -> 再检索，验证持久化与语义排序。 */
    @Test
    public void jdbcVectorStoreEndToEnd() throws Exception {
        Environment env = new Environment();
        String url = env.getProperty("summer.datasource.url");
        String user = env.getProperty("summer.datasource.username");
        String pass = env.getProperty("summer.datasource.password");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "无 summer.datasource.url 配置，跳过 pgvector 测试");

        DriverManager.setLoginTimeout(10);
        DataSource ds = new SimpleDataSource(url, user, pass);
        assumePgVectorAvailable(ds);
        dropTable(ds);

        StubEmbeddingModel embedding = new StubEmbeddingModel();
        JdbcVectorStore store = new JdbcVectorStore(new SqlExecutor(ds), embedding, TABLE, 0, true, true);

        List<String> ids = store.add(List.of(
                Document.of("java is a programming language"),
                Document.of("python is a programming language"),
                Document.of("the weather is nice today")));
        Assertions.assertEquals(3, ids.size(), "写入应返回 3 个 id");

        List<RetrievalResult> results = store.similaritySearch(SearchRequest.builder()
                .query("programming language").topK(2).build());
        Assertions.assertFalse(results.isEmpty(), "检索应命中");
        Assertions.assertTrue(results.size() <= 2, "topK=2 结果不应超过 2 条");
        Assertions.assertTrue(results.get(0).document().content().contains("programming"),
                "最相关结果应含 programming，实际: " + results.get(0).document().content());
        Assertions.assertTrue(results.get(0).score() >= results.get(results.size() - 1).score(),
                "结果应按相似度降序");

        store.delete(List.of(ids.get(0)));
        List<RetrievalResult> after = store.similaritySearch(SearchRequest.builder()
                .query("java programming").topK(5).build());
        Assertions.assertTrue(after.stream().noneMatch(r -> r.document().content().startsWith("java is")),
                "删除后不应再命中已删除的 java 文档");

        dropTable(ds);
    }

    /** 元数据过滤：写入带 source 元数据的文档，按 source=api 过滤检索，验证 JSONB @> 包含语义生效。 */
    @Test
    public void jdbcVectorStoreMetadataFilter() throws Exception {
        Environment env = new Environment();
        String url = env.getProperty("summer.datasource.url");
        String user = env.getProperty("summer.datasource.username");
        String pass = env.getProperty("summer.datasource.password");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "无 summer.datasource.url 配置，跳过 pgvector 元数据过滤测试");

        DriverManager.setLoginTimeout(10);
        DataSource ds = new SimpleDataSource(url, user, pass);
        assumePgVectorAvailable(ds);
        dropTable(ds);

        StubEmbeddingModel embedding = new StubEmbeddingModel();
        JdbcVectorStore store = new JdbcVectorStore(new SqlExecutor(ds), embedding, TABLE, 0, true, true);

        store.add(List.of(
                Document.builder().content("java programming language").metadata("source", "api").build(),
                Document.builder().content("python programming language").metadata("source", "doc").build(),
                Document.builder().content("java programming tools").metadata("source", "api").build()));

        List<RetrievalResult> filtered = store.similaritySearch(SearchRequest.builder()
                .query("programming language").topK(10).filter("source", "api").build());
        Assertions.assertEquals(2, filtered.size(), "source=api 过滤应仅命中 2 条");
        Assertions.assertTrue(filtered.stream().allMatch(r -> "api".equals(r.document().metadata().get("source"))),
                "过滤后应仅含 source=api 文档");

        List<RetrievalResult> all = store.similaritySearch(SearchRequest.builder()
                .query("programming").topK(10).build());
        Assertions.assertEquals(3, all.size(), "无过滤应返回全部 3 条文档");

        dropTable(ds);
    }

    /** 探测 PG 可达且 pgvector 扩展已启用，否则跳过本测试。 */
    private static void assumePgVectorAvailable(DataSource ds) {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname='vector')")) {
            rs.next();
            Assumptions.assumeTrue(rs.getBoolean(1), "pgvector 扩展未启用，跳过");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "PG 不可达，跳过: " + e.getMessage());
        }
    }

    /** 删除测试表，保证用例幂等。 */
    private static void dropTable(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + TABLE);
        } catch (SQLException ignored) {
            // 清理失败不阻断测试
        }
    }

    /** 最小 DataSource 实现：每次 getConnection 经 DriverManager 新建连接，仅供测试使用。 */
    private static final class SimpleDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException("unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }
}
