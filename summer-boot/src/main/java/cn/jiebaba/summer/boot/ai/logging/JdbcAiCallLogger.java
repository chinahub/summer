package cn.jiebaba.summer.boot.ai.logging;

import cn.jiebaba.summer.ai.logging.AiCallLog;
import cn.jiebaba.summer.ai.logging.AiCallLogger;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于 JDBC 的大模型调用日志实现：将每次调用记录写入 ai_call_log 表（首次写入时惰性建表）。
 * 复用 summer-data 的 {@link SqlExecutor} 执行 SQL，表名可配置（默认 ai_call_log）。
 * 表结构：id BIGSERIAL PK、create_time 默认当前时间、model/token 用量/耗时/成败/错误/提问摘要。
 */
public class JdbcAiCallLogger implements AiCallLogger {

    /** 合法表名标识符，防 SQL 注入。 */
    private static final Pattern IDENT = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final SqlExecutor sqlExecutor;
    private final String table;
    private volatile boolean initialized = false;

    public JdbcAiCallLogger(SqlExecutor sqlExecutor, String table) {
        if (sqlExecutor == null) {
            throw new IllegalArgumentException("sqlExecutor 不能为空");
        }
        if (table == null || !IDENT.matcher(table).matches()) {
            throw new IllegalArgumentException("非法表名: " + table);
        }
        this.sqlExecutor = sqlExecutor;
        this.table = table;
    }

    public JdbcAiCallLogger(SqlExecutor sqlExecutor) {
        this(sqlExecutor, "ai_call_log");
    }

    /** 写入一条调用日志；首次调用时惰性建表。 */
    @Override
    public void log(AiCallLog record) {
        if (record == null) {
            return;
        }
        ensureInitialized();
        String sql = "INSERT INTO " + table
                + " (model, prompt_tokens, completion_tokens, total_tokens, latency_millis, success, error_message, query_summary)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        sqlExecutor.update(new SqlBuilder.Sql(sql, Arrays.asList(
                record.model(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.latencyMillis(),
                record.success(),
                record.errorMessage(),
                record.querySummary())));
    }

    /** 首次写入时建表（id 自增主键、create_time 默认当前时间）。 */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            sqlExecutor.update(new SqlBuilder.Sql("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "model TEXT,"
                    + "prompt_tokens INT,"
                    + "completion_tokens INT,"
                    + "total_tokens INT,"
                    + "latency_millis BIGINT,"
                    + "success BOOLEAN NOT NULL,"
                    + "error_message TEXT,"
                    + "query_summary TEXT)", List.of()));
            initialized = true;
        }
    }
}
