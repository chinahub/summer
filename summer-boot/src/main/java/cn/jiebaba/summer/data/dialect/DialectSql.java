package cn.jiebaba.summer.data.dialect;

import java.util.List;

/** 方言 SQL 共享拼装助手（upsert 等语句生成用，包内可见）。 */
final class DialectSql {

    private DialectSql() {}

    /** 逗号分隔的 {@code ?} 占位符串。 */
    static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append('?');
        }
        return sb.toString();
    }

    /** 逗号分隔的转义列名串（按方言 quote 规则）。 */
    static String quotedJoin(List<String> columns, Dialect dialect) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(dialect.quote(columns.get(i)));
        }
        return sb.toString();
    }
}
