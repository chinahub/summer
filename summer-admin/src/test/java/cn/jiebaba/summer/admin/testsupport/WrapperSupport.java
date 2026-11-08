package cn.jiebaba.summer.admin.testsupport;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper 内存求值器：仅供单测的内存 Mapper 使用。按 {@code segments}/{@code params}
 * 顺序求值条件（仅支持 AND 连接，OR/嵌套条件直接抛错），再按 {@code orderByClause}
 * 稳定排序。不追求 SQL 完备，只覆盖服务实际用到的条件形态：
 * {@code col = ? / <> / > / >= / < / <= / LIKE ? / IS NULL / IS NOT NULL}。
 *
 * <p>排序字段值必须实现 {@link Comparable}；null 值排在最后（降序时为最前）。
 */
public final class WrapperSupport {

    private static final Pattern CONDITION =
            Pattern.compile("^(\\w+)\\s*(=|<>|>=|<=|>|<)\\s+\\?$");
    private static final Pattern LIKE = Pattern.compile("^(\\w+)\\s+LIKE\\s+\\?$");
    private static final Pattern IS_NULL = Pattern.compile("^(\\w+)\\s+IS( NOT)? NULL$");

    private WrapperSupport() {
    }

    /**
     * 按 Wrapper 条件过滤并排序行集合。
     *
     * @param rows     全量行（含已逻辑删除行，由 Wrapper 条件自行过滤）
     * @param wrapper  条件构造器
     * @param accessor 按列名（或属性名）提取行值的访问器
     * @param <T>      行类型
     * @return 过滤并排序后的列表
     */
    public static <T> List<T> select(List<T> rows, AbstractWrapper<T, ?> wrapper,
            Function<String, Function<T, Object>> accessor) {
        List<T> matched = new ArrayList<>();
        for (T row : rows) {
            if (matches(row, wrapper, accessor)) {
                matched.add(row);
            }
        }
        return sort(matched, wrapper.orderByClause(), accessor);
    }

    /** 求值单行是否满足全部条件段（AND 连接）。 */
    private static <T> boolean matches(T row, AbstractWrapper<T, ?> wrapper,
            Function<String, Function<T, Object>> accessor) {
        List<String> segments = wrapper.segments();
        List<Object> params = wrapper.params();
        int paramIndex = 0;
        for (String segment : segments) {
            if ("OR".equals(segment) || segment.startsWith("(")) {
                throw new UnsupportedOperationException("内存求值器不支持该条件: " + segment);
            }
            Matcher condition = CONDITION.matcher(segment);
            if (condition.matches()) {
                Object value = params.get(paramIndex++);
                if (!test(accessor.apply(condition.group(1)).apply(row),
                        condition.group(2), value)) {
                    return false;
                }
                continue;
            }
            Matcher like = LIKE.matcher(segment);
            if (like.matches()) {
                Object value = params.get(paramIndex++);
                if (!like(accessor.apply(like.group(1)).apply(row), value)) {
                    return false;
                }
                continue;
            }
            Matcher isNull = IS_NULL.matcher(segment);
            if (isNull.matches()) {
                boolean nullValue = accessor.apply(isNull.group(1)).apply(row) == null;
                if (isNull.group(2) != null ? nullValue : !nullValue) {
                    return false;
                }
                continue;
            }
            throw new UnsupportedOperationException("内存求值器不支持该条件: " + segment);
        }
        return true;
    }

    /** 求值比较条件：行值与参数按操作符比较，行值为 null 时一律不满足。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean test(Object rowValue, String op, Object param) {
        if (rowValue == null) {
            return false;
        }
        return switch (op) {
            case "=" -> Objects.equals(rowValue, param);
            case "<>" -> !Objects.equals(rowValue, param);
            case ">" -> ((Comparable) rowValue).compareTo(param) > 0;
            case ">=" -> ((Comparable) rowValue).compareTo(param) >= 0;
            case "<" -> ((Comparable) rowValue).compareTo(param) < 0;
            case "<=" -> ((Comparable) rowValue).compareTo(param) <= 0;
            default -> throw new UnsupportedOperationException("不支持的操作符: " + op);
        };
    }

    /** 求值 LIKE 条件：支持 {@code %} 与 {@code _} 通配，大小写敏感。 */
    private static boolean like(Object rowValue, Object param) {
        if (rowValue == null || param == null) {
            return false;
        }
        String text = rowValue.toString();
        String pattern = param.toString();
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '%' -> regex.append(".*");
                case '_' -> regex.append('.');
                default -> {
                    if (".[]{}()+-^$?*\\|".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        return text.matches(regex.toString());
    }

    /** 按 ORDER BY 子句稳定排序；字段值须实现 Comparable，null 值排最后（降序时最前）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> List<T> sort(List<T> rows, String orderByClause,
            Function<String, Function<T, Object>> accessor) {
        String clause = orderByClause == null ? "" : orderByClause.trim();
        if (clause.isEmpty()) {
            return rows;
        }
        if (clause.startsWith("ORDER BY")) {
            clause = clause.substring("ORDER BY".length()).trim();
        }
        List<T> sorted = new ArrayList<>(rows);
        String[] items = clause.split(",");
        Comparator<T> comparator = null;
        for (String item : items) {
            String[] parts = item.trim().split("\\s+");
            String column = parts[0];
            boolean asc = parts.length < 2 || !"DESC".equalsIgnoreCase(parts[1]);
            Comparator<T> columnComparator = Comparator.comparing(
                    row -> (Comparable) accessor.apply(column).apply(row),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            comparator = comparator == null
                    ? (asc ? columnComparator : columnComparator.reversed())
                    : comparator.thenComparing(asc ? columnComparator : columnComparator.reversed());
        }
        sorted.sort(comparator);
        return sorted;
    }
}
