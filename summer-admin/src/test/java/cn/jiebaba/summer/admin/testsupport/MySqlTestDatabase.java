package cn.jiebaba.summer.admin.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据库工具：只重建 {@code summer_admin_test}，并执行与开发库同一份
 * {@code sql/summer_admin.sql} 初始化脚本。
 *
 * <p>硬性约束：
 * <ul>
 *   <li>连接凭据只从环境变量 {@code SUMMER_DATASOURCE_USERNAME} /
 *       {@code SUMMER_DATASOURCE_PASSWORD} 读取，不允许写入任何文件或日志；</li>
 *   <li>{@link #reset()} 只允许 DROP/CREATE {@value #TEST_DATABASE}，
 *       通过 {@link #assertSafeDatabase()} 对库名做断言防护，绝不触碰 {@code summer_admin}；</li>
 *   <li>执行脚本时会跳过脚本头部的建库/切库语句（{@code CREATE DATABASE} /
 *       {@code DROP DATABASE} / {@code USE}），保证语句全部落在测试库内。</li>
 * </ul>
 */
public final class MySqlTestDatabase {

    /** 测试专用数据库名，全部 DB 测试共享。 */
    public static final String TEST_DATABASE = "summer_admin_test";

    /** 开发/生产库名，测试中严禁触碰。 */
    public static final String PRODUCTION_DATABASE = "summer_admin";

    private static final String BASE_URL = "jdbc:mysql://localhost:3306";

    private MySqlTestDatabase() {}

    /** 返回测试库名，供隔离断言使用。 */
    public static String testDatabaseName() {
        return TEST_DATABASE;
    }

    /** 返回测试库 JDBC URL（应用测试配置使用同一地址）。 */
    public static String testJdbcUrl() {
        return BASE_URL + "/" + TEST_DATABASE
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    }

    /**
     * 重建测试库并执行初始化脚本：DROP + CREATE {@value #TEST_DATABASE}，
     * 随后以该库为当前库执行 {@code sql/summer_admin.sql}。
     * 只允许在 {@value #TEST_DATABASE} 上操作，库名不通过则直接抛错。
     */
    public static void reset() {
        assertSafeDatabase();
        String username = requiredEnv("SUMMER_DATASOURCE_USERNAME");
        String password = requiredEnv("SUMMER_DATASOURCE_PASSWORD");
        try (Connection conn = DriverManager.getConnection(BASE_URL, username, password);
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DATABASE);
            st.execute("CREATE DATABASE " + TEST_DATABASE
                    + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("重建测试数据库失败: " + TEST_DATABASE, e);
        }
        executeScript();
    }

    /**
     * 在当前测试库上重复执行初始化脚本（不重建库），用于验证脚本可重复执行
     * 且不覆盖已有业务修改。执行前再次校验库名安全。
     */
    public static void executeScript() {
        assertSafeDatabase();
        List<String> statements = parseScript(loadScript());
        String username = requiredEnv("SUMMER_DATASOURCE_USERNAME");
        String password = requiredEnv("SUMMER_DATASOURCE_PASSWORD");
        try (Connection conn = DriverManager.getConnection(
                BASE_URL + "/" + TEST_DATABASE, username, password);
             Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("执行初始化脚本失败，数据库: " + TEST_DATABASE, e);
        }
    }

    /** 从模块工作目录读取初始化脚本（surefire 工作目录即 summer-admin 模块根）。 */
    private static String loadScript() {
        Path path = Path.of(System.getProperty("user.dir"), "sql", "summer_admin.sql");
        if (!Files.exists(path)) {
            throw new IllegalStateException("初始化脚本不存在: " + path.toAbsolutePath());
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 将脚本切分为单条语句：忽略空行与 {@code --} 整行注释，按分号收尾切分；
     * 过滤 {@code CREATE DATABASE} / {@code DROP DATABASE} / {@code USE} 语句，
     * 使脚本可以在已选定的测试库上安全重放。
     */
    static List<String> parseScript(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            current.append(line).append(' ');
            if (line.endsWith(";")) {
                String sql = current.toString().trim();
                current.setLength(0);
                sql = sql.substring(0, sql.length() - 1).trim();
                if (!sql.isEmpty() && !isDatabaseScoped(sql)) {
                    statements.add(sql);
                }
            }
        }
        if (!current.toString().trim().isEmpty()) {
            throw new IllegalStateException("初始化脚本末尾存在未以分号结束的内容");
        }
        return statements;
    }

    /** 判断语句是否作用于整个数据库（建库/删库/切库），这类语句在测试重放时必须跳过。 */
    private static boolean isDatabaseScoped(String sql) {
        String upper = sql.toUpperCase();
        return upper.startsWith("CREATE DATABASE") || upper.startsWith("DROP DATABASE")
                || upper.startsWith("USE ");
    }

    /** 防护：只有 TEST_DATABASE 允许被重建，且绝不等于生产库名。 */
    private static void assertSafeDatabase() {
        if (!"summer_admin_test".equals(TEST_DATABASE) || TEST_DATABASE.equals(PRODUCTION_DATABASE)
                || TEST_DATABASE.isBlank()) {
            throw new IllegalStateException("测试数据库名非法，拒绝执行: " + TEST_DATABASE);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name + "，无法连接测试数据库");
        }
        return value;
    }
}
