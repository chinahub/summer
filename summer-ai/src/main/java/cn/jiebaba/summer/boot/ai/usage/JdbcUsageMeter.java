package cn.jiebaba.summer.boot.ai.usage;

import cn.jiebaba.summer.ai.usage.UsageMeter;
import cn.jiebaba.summer.ai.usage.UsageRecord;
import cn.jiebaba.summer.ai.usage.UsageTotal;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC 版 {@link UsageMeter}：用量记录持久化到表（默认 {@code ai_usage}），
 * 首次写入时惰性建表（CREATE TABLE IF NOT EXISTS）；聚合查询读回记录后在内存规约
 * （标签为动态键值，避免绑定特定数据库的 JSON SQL 方言）。适合长周期计量与重启累计；
 * 超大规模报表建议在此基础上自建物化汇总。
 */
public final class JdbcUsageMeter implements UsageMeter {

    /** 默认用量表名。 */
    public static final String DEFAULT_TABLE = "ai_usage";

    private final SqlExecutor executor;
    private final String table;
    private volatile boolean tableEnsured;

    public JdbcUsageMeter(SqlExecutor executor) {
        this(executor, DEFAULT_TABLE);
    }

    public JdbcUsageMeter(SqlExecutor executor, String table) {
        if (executor == null) throw new IllegalArgumentException("executor 不能为空");
        if (table == null || table.isBlank()) throw new IllegalArgumentException("table 不能为空");
        this.executor = executor;
        this.table = table;
    }

    @Override
    public void record(UsageRecord record) {
        if (record == null) throw new IllegalArgumentException("record 不能为空");
        ensureTable();
        StringBuilder tags = new StringBuilder();
        for (Map.Entry<String, String> e : record.tags().entrySet()) {
            if (tags.length() > 0) tags.append('\n');
            tags.append(sanitize(e.getKey())).append('=').append(sanitize(e.getValue()));
        }
        executor.update(new SqlBuilder.Sql(
                "INSERT INTO " + quote(table)
                        + " (model, tags, prompt_tokens, completion_tokens, calls, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                List.of(record.model(), tags.toString(),
                        record.promptTokens(), record.completionTokens(), record.calls(),
                        new Timestamp(record.timestamp()))));
    }

    @Override
    public UsageTotal sum(Map<String, String> tagFilter) {
        UsageTotal total = UsageTotal.ZERO;
        for (UsageRecord r : loadAll()) {
            if (matches(r, tagFilter)) total = total.plus(toTotal(r));
        }
        return total;
    }

    @Override
    public Map<String, UsageTotal> sumGroupedBy(String tagKey, Map<String, String> tagFilter) {
        Map<String, UsageTotal> grouped = new LinkedHashMap<>();
        for (UsageRecord r : loadAll()) {
            if (!matches(r, tagFilter)) continue;
            String key = r.tags().getOrDefault(tagKey, "");
            grouped.merge(key, toTotal(r), UsageTotal::plus);
        }
        return grouped;
    }

    /** 读回全部用量记录（tags 由 k=v 行文本还原）。 */
    private List<UsageRecord> loadAll() {
        ensureTable();
        return executor.query(new SqlBuilder.Sql(
                "SELECT model, tags, prompt_tokens, completion_tokens, calls, created_at FROM " + quote(table),
                List.of()), (rs, i) -> new UsageRecord(
                rs.getString(1), parseTags(rs.getString(2)),
                rs.getLong(3), rs.getLong(4), rs.getLong(5),
                rs.getTimestamp(6) == null ? 0L : rs.getTimestamp(6).getTime()));
    }

    /** 首次访问时建表（幂等）。 */
    private void ensureTable() {
        if (tableEnsured) return;
        synchronized (this) {
            if (tableEnsured) return;
            executor.update(new SqlBuilder.Sql(
                    "CREATE TABLE IF NOT EXISTS " + quote(table) + " ("
                            + "model VARCHAR(200), "
                            + "tags TEXT, "
                            + "prompt_tokens BIGINT, "
                            + "completion_tokens BIGINT, "
                            + "calls BIGINT, "
                            + "created_at TIMESTAMP)",
                    List.of()));
            tableEnsured = true;
        }
    }

    private static Map<String, String> parseTags(String text) {
        if (text == null || text.isBlank()) return Map.of();
        Map<String, String> tags = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) tags.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return Map.copyOf(tags);
    }

    /** 标签键值去换行（存储格式为 k=v 按行分隔，换行会破坏还原）。 */
    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static boolean matches(UsageRecord r, Map<String, String> tagFilter) {
        if (tagFilter == null || tagFilter.isEmpty()) return true;
        for (Map.Entry<String, String> e : tagFilter.entrySet()) {
            if (!e.getValue().equals(r.tags().get(e.getKey()))) return false;
        }
        return true;
    }

    private static UsageTotal toTotal(UsageRecord r) {
        return new UsageTotal(r.promptTokens(), r.completionTokens(), r.calls());
    }

    private String quote(String identifier) {
        return executor.dialect().quote(identifier);
    }
}
