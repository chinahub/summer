package cn.jiebaba.summer.test.typehandler;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.data.annotation.TableField;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.dialect.MySqlDialect;
import cn.jiebaba.summer.data.dialect.OracleDialect;
import cn.jiebaba.summer.data.dialect.PostgreSqlDialect;
import cn.jiebaba.summer.data.dialect.SqlServerDialect;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.metadata.TableFieldInfo;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.support.JdbcValue;
import cn.jiebaba.summer.data.support.JsonTypeHandler;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.TypeHandler;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在无真实数据库的情况下，端到端验证 TypeHandler + 方言驱动的 JSON 绑定路径：
 * SqlBuilder 将 handler 字段包装为 {@link JdbcValue}，{@link JsonTypeHandler}
 * 通过 JsonUtil 序列化并把原生绑定委托给当前 {@link Dialect}
 * （PostgreSQL 用 PGobject(jsonb)、MySQL 用 setString），读取路径将 JSON 反序列化回对象，
 * 并由 {@link Dialect#fromUrl} 从 JDBC URL 推断方言。
 */
public class JsonTypeHandlerTest {

    public static class JsonDemo {
        private Long id;
        @TableField(typeHandler = JsonTypeHandler.class)
        private Map<String, Object> config;
        public JsonDemo() {}
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    @Test
    void metadataAttachesJsonHandler() {
        TableInfo table = MetadataParser.parse(JsonDemo.class);
        TableFieldInfo configField = table.field("config");
        Assert.assertNotNull(configField, "config field missing");
        Assert.assertNotNull(configField.typeHandler(), "typeHandler should be attached");
        Assert.assertTrue(configField.typeHandler() instanceof JsonTypeHandler,
                "expected JsonTypeHandler, got " + configField.typeHandler());
        Assert.assertNull(table.field("id").typeHandler(), "id should have no handler");
    }

    @Test
    void insertWrapsHandlerFieldIntoJdbcValue() {
        TableInfo table = MetadataParser.parse(JsonDemo.class);
        JsonDemo demo = new JsonDemo();
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("name", "alice");
        demo.setConfig(cfg);

        SqlBuilder builder = new SqlBuilder(table);
        SqlBuilder.Sql sql = builder.insert(demo);

        boolean found = false;
        for (Object p : sql.params()) {
            if (p instanceof JdbcValue jv && jv.value() == cfg && jv.handler() instanceof JsonTypeHandler) {
                found = true;
            }
        }
        Assert.assertTrue(found, "config param should be wrapped in JdbcValue; params=" + sql.params());
        Assert.assertTrue(sql.sql().contains("config"), "insert should reference config column: " + sql.sql());
    }

    @Test
    void pgDialectBindsJsonbPgObject() throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("name", "alice");
        cfg.put("age", 30);
        Recorder ps = new Recorder();
        new JsonTypeHandler().setParameter(ps.ps, 1, cfg, new PostgreSqlDialect());

        Assert.assertNotNull(ps.lastObject, "expected setObject call on pg");
        Assert.assertEquals("jsonb", ps.lastObject.getClass().getMethod("getType").invoke(ps.lastObject));
        String value = (String) ps.lastObject.getClass().getMethod("getValue").invoke(ps.lastObject);
        Assert.assertTrue(value.contains("\"alice\""), "json should contain alice: " + value);
    }

    @Test
    void mysqlDialectBindsJsonString() throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("k", "v");
        Recorder ps = new Recorder();
        new JsonTypeHandler().setParameter(ps.ps, 1, cfg, new MySqlDialect());

        Assert.assertNotNull(ps.lastString, "expected setString call on mysql");
        Assert.assertTrue(ps.lastString.contains("\"k\""), "json should contain k: " + ps.lastString);
        Assert.assertNull(ps.lastObject, "mysql should not call setObject");
    }

    @Test
    void oracleDialectColumnTypeIsClob() {
        Assert.assertEquals("CLOB", new OracleDialect().jsonColumnType());
    }

    @Test
    void getResultDeserializesJson() throws Exception {
        RsRecorder rs = new RsRecorder("{\"a\":1,\"b\":\"x\"}");
        Object out = new JsonTypeHandler().getResult(rs.rs, 1, Map.class, new PostgreSqlDialect());
        Assert.assertTrue(out instanceof Map, "expected Map, got " + out);
        Map<?, ?> map = (Map<?, ?>) out;
        Assert.assertEquals("x", map.get("b"), "b should be x");
        Assert.assertEquals(2, map.size(), "map size");
    }

    @Test
    void inferDialectFromUrl() {
        Assert.assertTrue(Dialect.fromUrl("jdbc:postgresql://localhost/db") instanceof PostgreSqlDialect);
        Assert.assertTrue(Dialect.fromUrl("jdbc:mysql://localhost/db") instanceof MySqlDialect);
        Assert.assertTrue(Dialect.fromUrl("jdbc:mariadb://localhost/db") instanceof MySqlDialect);
        Assert.assertTrue(Dialect.fromUrl("jdbc:oracle:thin:@//host:1521/db") instanceof OracleDialect);
        Assert.assertTrue(Dialect.fromUrl("jdbc:sqlserver://host:1433;databaseName=db") instanceof SqlServerDialect);
    }

    @Test
    void fromDriverMapsKnownDrivers() {
        Assert.assertTrue(Dialect.fromDriver("org.postgresql.Driver") instanceof PostgreSqlDialect);
        Assert.assertTrue(Dialect.fromDriver("com.mysql.cj.jdbc.Driver") instanceof MySqlDialect);
        Assert.assertTrue(Dialect.fromDriver("com.mysql.jdbc.Driver") instanceof MySqlDialect);
        Assert.assertTrue(Dialect.fromDriver("org.mariadb.jdbc.Driver") instanceof MySqlDialect);
        Assert.assertTrue(Dialect.fromDriver("oracle.jdbc.OracleDriver") instanceof OracleDialect);
        Assert.assertTrue(Dialect.fromDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver") instanceof SqlServerDialect);
        Assert.assertNull(Dialect.fromDriver(""), "empty driver should map to null");
        Assert.assertNull(Dialect.fromDriver(null), "null driver should map to null");
    }

    @Test
    void detectPrefersDriverOverUrl() {
        Assert.assertTrue(Dialect.detect("org.postgresql.Driver", "jdbc:mysql://localhost/db") instanceof PostgreSqlDialect);
        Assert.assertTrue(Dialect.detect("com.mysql.cj.jdbc.Driver", "jdbc:postgresql://localhost/db") instanceof MySqlDialect);
    }

    @Test
    void detectFallsBackToUrlWhenDriverAbsent() {
        Assert.assertTrue(Dialect.detect("", "jdbc:postgresql://localhost/db") instanceof PostgreSqlDialect);
        Assert.assertTrue(Dialect.detect(null, "jdbc:mysql://localhost/db") instanceof MySqlDialect);
    }

    static final class Recorder {
        final PreparedStatement ps;
        Object lastObject;
        String lastString;
        Recorder() {
            ps = (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        String n = method.getName();
                        if (n.equals("setObject") && args.length >= 2) lastObject = args[1];
                        else if (n.equals("setString") && args.length >= 2) lastString = (String) args[1];
                        return null;
                    });
        }
    }

    static final class RsRecorder {
        final ResultSet rs;
        final String json;
        RsRecorder(String json) {
            this.json = json;
            rs = (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getString") && args != null && args.length == 1) return json;
                        return null;
                    });
        }
    }
}
