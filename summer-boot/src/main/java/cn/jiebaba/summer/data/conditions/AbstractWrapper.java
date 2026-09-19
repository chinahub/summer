package cn.jiebaba.summer.data.conditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基础条件构造器，借鉴 MyBatis-Plus 的 Wrapper。使用自身类型泛型 {@code W}，
 * 使链式调用保留具体 Wrapper 类型。
 */
public abstract class AbstractWrapper<T, W extends AbstractWrapper<T, W>> {

    /** or() 插入的连接符标记段，拼接 SQL 时识别为 OR 连接符。 */
    private static final String OR_SEGMENT = "OR";

    protected final List<String> segments = new ArrayList<>();
    protected final List<Object> params = new ArrayList<>();
    protected final List<String> orderBy = new ArrayList<>();
    protected final List<String> groupBy = new ArrayList<>();
    protected String lastSql = "";

    @SuppressWarnings("unchecked")
    protected final W self() { return (W) this; }

    /** 将列引用转换为实际的 SQL 列名。 */
    protected abstract String column(String reference);

    public W eq(String column, Object value) { return addCondition(column, "=", value); }
    public W ne(String column, Object value) { return addCondition(column, "<>", value); }
    public W gt(String column, Object value) { return addCondition(column, ">", value); }
    public W ge(String column, Object value) { return addCondition(column, ">=", value); }
    public W lt(String column, Object value) { return addCondition(column, "<", value); }
    public W le(String column, Object value) { return addCondition(column, "<=", value); }

    public W like(String column, Object value) { return addLike(column, "LIKE", "%" + value + "%"); }
    public W likeLeft(String column, Object value) { return addLike(column, "LIKE", "%" + value); }
    public W likeRight(String column, Object value) { return addLike(column, "LIKE", value + "%"); }
    public W notLike(String column, Object value) { return addLike(column, "NOT LIKE", "%" + value + "%"); }

    public W isNull(String column) { segments.add(column(column) + " IS NULL"); return self(); }
    public W isNotNull(String column) { segments.add(column(column) + " IS NOT NULL"); return self(); }

    public W in(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) { segments.add("1=0"); return self(); }
        StringBuilder placeholders = new StringBuilder("(");
        boolean first = true;
        for (Object v : values) {
            if (!first) placeholders.append(',');
            first = false;
            placeholders.append('?');
            params.add(v);
        }
        placeholders.append(')');
        segments.add(column(column) + " IN " + placeholders);
        return self();
    }

    public W notIn(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) return self();
        StringBuilder placeholders = new StringBuilder("(");
        boolean first = true;
        for (Object v : values) {
            if (!first) placeholders.append(',');
            first = false;
            placeholders.append('?');
            params.add(v);
        }
        placeholders.append(')');
        segments.add(column(column) + " NOT IN " + placeholders);
        return self();
    }

    public W between(String column, Object lo, Object hi) {
        segments.add(column(column) + " BETWEEN ? AND ?");
        params.add(lo); params.add(hi);
        return self();
    }

    public W orderByAsc(String column) { return orderBy(column, true); }
    public W orderByDesc(String column) { return orderBy(column, false); }

    public W orderBy(String column, boolean asc) {
        orderBy.add(column(column) + (asc ? " ASC" : " DESC"));
        return self();
    }

    public W groupBy(String column) { groupBy.add(column(column)); return self(); }

    public W last(String sql) { this.lastSql = sql; return self(); }

    public W and(Consumer<W> consumer) { return nest("AND", consumer); }
    public W or(Consumer<W> consumer) { return nest("OR", consumer); }

    /**
     * 将下一个条件的连接符切换为 OR（对齐 MyBatis-Plus 语义，只影响紧随其后的一个条件）。
     * 插入字面量 "OR" 段作为连接符标记，拼接 SQL 时由 {@link #joinSegments} 识别。
     */
    public W or() {
        if (!segments.isEmpty() && !OR_SEGMENT.equals(segments.getLast())) segments.add(OR_SEGMENT);
        return self();
    }

    @SuppressWarnings("unchecked")
    private W nest(String connector, Consumer<W> consumer) {
        AbstractWrapper<T, W> nested;
        try {
            nested = (AbstractWrapper<T, W>) getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        consumer.accept((W) nested);
        if (nested.segments.isEmpty()) return self();
        // AND 是默认连接符无需标记；OR 需插入连接符段由 joinSegments 识别
        if (!segments.isEmpty() && OR_SEGMENT.equals(connector)) segments.add(OR_SEGMENT);
        segments.add("(" + joinSegments(nested.segments) + ")");
        params.addAll(nested.params);
        return self();
    }

    private W addCondition(String column, String op, Object value) {
        if (value == null) return self();
        segments.add(column(column) + " " + op + " ?");
        params.add(value);
        return self();
    }

    private W addLike(String column, String op, String pattern) {
        segments.add(column(column) + " " + op + " ?");
        params.add(pattern);
        return self();
    }

    public String sqlSegment() {
        if (segments.isEmpty()) return "";
        return joinSegments(segments);
    }

    /**
     * 拼接条件段为 SQL：字面量 "OR" 段（{@link #or()} 插入的连接符标记）使其后一个
     * 条件以 OR 连接，其余相邻条件默认以 AND 连接；连续或末尾孤悬的 OR 自动忽略。
     * AND 优先级高于 OR，与 MyBatis-Plus 顶层链式语义一致。
     */
    public static String joinSegments(List<String> segments) {
        StringBuilder sb = new StringBuilder();
        String connector = null;
        for (String seg : segments) {
            if (OR_SEGMENT.equals(seg)) { connector = " OR "; continue; }
            if (!sb.isEmpty()) sb.append(connector != null ? connector : " AND ");
            sb.append(seg);
            connector = null;
        }
        return sb.toString();
    }

    public List<Object> params() { return params; }
    public List<String> segments() { return segments; }

    public String orderByClause() {
        if (orderBy.isEmpty()) return "";
        return " ORDER BY " + String.join(", ", orderBy);
    }

    public String groupByClause() {
        if (groupBy.isEmpty()) return "";
        return " GROUP BY " + String.join(", ", groupBy);
    }

    public String lastSql() { return lastSql; }
    public boolean isEmpty() { return segments.isEmpty(); }
}
