package cn.jiebaba.summer.test.data;

import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.migration.DatabaseMigration;
import cn.jiebaba.summer.data.migration.SchemaMigrator;
import cn.jiebaba.summer.data.support.DataAccessException;
import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.data.transaction.TransactionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SchemaMigrator} 单测：版本升序执行、幂等跳过、版本号校验、失败回滚中止、
 * 历史表缺失时自动建表。使用内置迷你假库（JDK 代理模拟 JDBC），不依赖真实数据库。
 */
public class SchemaMigratorTest {

    /** 迷你假库：记录 SQL 与迁移历史，按语句形态返回最小结果集。 */
    static final class FakeDb {
        final List<String> sqlLog = new ArrayList<>();
        final List<Integer> historyRows = new ArrayList<>();
        int commits;
        boolean historyTableMissing;
        final Connection connection;
        final DataSource dataSource;

        FakeDb() {
            this.connection = (Connection) Proxy.newProxyInstance(
                    FakeDb.class.getClassLoader(), new Class<?>[]{Connection.class}, this::onConnection);
            this.dataSource = (DataSource) Proxy.newProxyInstance(
                    FakeDb.class.getClassLoader(), new Class<?>[]{DataSource.class}, (p, m, a) -> {
                        if (m.getName().equals("getConnection")) return connection;
                        return defaultValue(m.getReturnType());
                    });
        }

        private Object onConnection(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getName().equals("prepareStatement")) {
                String sql = (String) args[0];
                sqlLog.add(sql);
                return preparedStatement(sql);
            }
            if (method.getName().equals("commit")) {
                commits++;
            }
            return defaultValue(method.getReturnType());
        }

        private PreparedStatement preparedStatement(String sql) {
            Map<Integer, Object> params = new LinkedHashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(
                    FakeDb.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "setObject":
                            case "setString":
                            case "setLong":
                            case "setInt":
                            case "setTimestamp":
                                params.put((Integer) a[0], a[1]);
                                return null;
                            case "executeUpdate":
                                if (sql.contains("INSERT") && sql.contains("summer_schema_history")) {
                                    historyRows.add((Integer) params.get(1));
                                }
                                return 1;
                            case "executeQuery":
                                if (sql.contains("COUNT(*)") && historyTableMissing) {
                                    throw new SQLException("history table missing");
                                }
                                return resultSet(sql);
                            case "close":
                                return null;
                            default:
                                return defaultValue(m.getReturnType());
                        }
                    });
        }

        private ResultSet resultSet(String sql) {
            List<Object> rows = new ArrayList<>();
            if (sql.contains("COUNT(*)")) {
                rows.add(0L);
            } else {
                rows.addAll(historyRows);
            }
            int[] cursor = {-1};
            return (ResultSet) Proxy.newProxyInstance(
                    FakeDb.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "next":
                                cursor[0]++;
                                return cursor[0] < rows.size();
                            case "getInt":
                                return ((Number) rows.get(cursor[0])).intValue();
                            case "getLong":
                                return ((Number) rows.get(cursor[0])).longValue();
                            case "close":
                                return null;
                            default:
                                return defaultValue(m.getReturnType());
                        }
                    });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private static DatabaseMigration migration(int version, List<Integer> order) {
        return new DatabaseMigration() {
            @Override public int version() { return version; }
            @Override public String description() { return "V" + version; }
            @Override public void migrate(SqlExecutor executor) { order.add(version); }
        };
    }

    private static SchemaMigrator migrator(FakeDb db) {
        return new SchemaMigrator(new SqlExecutor(db.dataSource, Dialect.of("postgresql")),
                null, SchemaMigrator.DEFAULT_HISTORY_TABLE);
    }

    @Test
    @DisplayName("按版本升序执行并记录历史，与声明顺序无关")
    void runsInVersionOrder() {
        FakeDb db = new FakeDb();
        List<Integer> order = new ArrayList<>();
        int applied = migrator(db).migrate(List.of(migration(2, order), migration(1, order)));
        Assertions.assertEquals(2, applied);
        Assertions.assertEquals(List.of(1, 2), order, "应按版本号升序执行");
        Assertions.assertEquals(List.of(1, 2), db.historyRows, "每个迁移完成后记入历史表");
    }

    @Test
    @DisplayName("重复启动幂等：已应用版本跳过")
    void skipsAlreadyApplied() {
        FakeDb db = new FakeDb();
        List<Integer> order = new ArrayList<>();
        migrator(db).migrate(List.of(migration(1, order), migration(2, order)));
        order.clear();
        int applied = migrator(db).migrate(List.of(migration(1, order), migration(2, order)));
        Assertions.assertEquals(0, applied, "已应用版本不应重复执行");
        Assertions.assertTrue(order.isEmpty());
    }

    @Test
    @DisplayName("版本号重复/非正整数被拒绝")
    void invalidVersionsRejected() {
        FakeDb db = new FakeDb();
        SchemaMigrator m = migrator(db);
        List<Integer> order = new ArrayList<>();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> m.migrate(List.of(migration(1, order), migration(1, order))));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> m.migrate(List.of(migration(0, order))));
    }

    @Test
    @DisplayName("迁移失败：抛 DataAccessException 中止，后续版本不执行")
    void failureAborts() {
        FakeDb db = new FakeDb();
        List<Integer> order = new ArrayList<>();
        DatabaseMigration bad = new DatabaseMigration() {
            @Override public int version() { return 2; }
            @Override public String description() { return "坏迁移"; }
            @Override public void migrate(SqlExecutor executor) { order.add(2); throw new IllegalStateException("炸"); }
        };
        DataAccessException e = Assertions.assertThrows(DataAccessException.class,
                () -> migrator(db).migrate(List.of(migration(1, order), bad, migration(3, order))));
        Assertions.assertTrue(e.getMessage().contains("V2"), e.getMessage());
        Assertions.assertEquals(List.of(1, 2), order, "失败后不应继续执行 V3");
        Assertions.assertEquals(List.of(1), db.historyRows, "失败迁移不应记入历史");
    }

    @Test
    @DisplayName("历史表缺失时自动建表")
    void createsHistoryTableWhenMissing() {
        FakeDb db = new FakeDb();
        db.historyTableMissing = true;
        List<Integer> order = new ArrayList<>();
        int applied = migrator(db).migrate(List.of(migration(1, order)));
        Assertions.assertEquals(1, applied);
        Assertions.assertTrue(db.sqlLog.stream().anyMatch(s -> s.startsWith("CREATE TABLE")),
                "应执行建表语句，实际: " + db.sqlLog);
    }

    @Test
    @DisplayName("注入事务管理器时迁移在事务内执行（独立提交一次）")
    void wrapsInTransactionWhenManagerPresent() {
        FakeDb db = new FakeDb();
        List<Integer> order = new ArrayList<>();
        TransactionManager tm = new TransactionManager(db.dataSource);
        SchemaMigrator m = new SchemaMigrator(new SqlExecutor(db.dataSource, Dialect.of("postgresql")),
                tm, SchemaMigrator.DEFAULT_HISTORY_TABLE);
        Assertions.assertEquals(1, m.migrate(List.of(migration(1, order))));
        Assertions.assertEquals(List.of(1), order);
        Assertions.assertEquals(1, db.commits, "迁移应在事务内独立提交一次");
        Assertions.assertEquals(List.of(1), db.historyRows);
    }
}
