package cn.jiebaba.summer.data.conditions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class QueryWrapper<T> extends AbstractWrapper<T, QueryWrapper<T>> {

    private final Set<String> selectColumns = new LinkedHashSet<>();

    @Override
    protected String column(String reference) { return reference; }

    public QueryWrapper<T> select(String... columns) {
        selectColumns.addAll(Arrays.asList(columns));
        return this;
    }

    public Set<String> selectColumns() { return selectColumns; }
    public boolean hasSelect() { return !selectColumns.isEmpty(); }

    public static <T> QueryWrapper<T> of() { return new QueryWrapper<>(); }
}
