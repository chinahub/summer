package cn.jiebaba.summer.data.conditions;

import java.util.ArrayList;
import java.util.List;

public class LambdaQueryWrapper<T> extends AbstractWrapper<T, LambdaQueryWrapper<T>> {

    private final List<String> selectProperties = new ArrayList<>();

    @Override
    protected String column(String reference) { return reference; }

    public LambdaQueryWrapper<T> select(SFunction<T, ?>... columns) {
        for (SFunction<T, ?> fn : columns) selectProperties.add(LambdaUtils.propertyName(fn));
        return this;
    }

    public LambdaQueryWrapper<T> eq(SFunction<T, ?> column, Object value) { return addLambda(column, "=", value); }
    public LambdaQueryWrapper<T> ne(SFunction<T, ?> column, Object value) { return addLambda(column, "<>", value); }
    public LambdaQueryWrapper<T> gt(SFunction<T, ?> column, Object value) { return addLambda(column, ">", value); }
    public LambdaQueryWrapper<T> ge(SFunction<T, ?> column, Object value) { return addLambda(column, ">=", value); }
    public LambdaQueryWrapper<T> lt(SFunction<T, ?> column, Object value) { return addLambda(column, "<", value); }
    public LambdaQueryWrapper<T> le(SFunction<T, ?> column, Object value) { return addLambda(column, "<=", value); }

    public LambdaQueryWrapper<T> like(SFunction<T, ?> column, Object value) { return addLambdaLike(column, "LIKE", "%" + value + "%"); }
    public LambdaQueryWrapper<T> likeLeft(SFunction<T, ?> column, Object value) { return addLambdaLike(column, "LIKE", "%" + value); }
    public LambdaQueryWrapper<T> likeRight(SFunction<T, ?> column, Object value) { return addLambdaLike(column, "LIKE", value + "%"); }

    public LambdaQueryWrapper<T> orderByAsc(SFunction<T, ?> column) { orderBy.add(LambdaUtils.propertyName(column) + " ASC"); return this; }
    public LambdaQueryWrapper<T> orderByDesc(SFunction<T, ?> column) { orderBy.add(LambdaUtils.propertyName(column) + " DESC"); return this; }
    public LambdaQueryWrapper<T> groupBy(SFunction<T, ?> column) { groupBy.add(LambdaUtils.propertyName(column)); return this; }

    public boolean hasSelect() { return !selectProperties.isEmpty(); }
    public List<String> selectProperties() { return selectProperties; }

    private LambdaQueryWrapper<T> addLambda(SFunction<T, ?> column, String op, Object value) {
        if (value == null) return this;
        segments.add(LambdaUtils.propertyName(column) + " " + op + " ?");
        params.add(value);
        return this;
    }

    private LambdaQueryWrapper<T> addLambdaLike(SFunction<T, ?> column, String op, String pattern) {
        segments.add(LambdaUtils.propertyName(column) + " " + op + " ?");
        params.add(pattern);
        return this;
    }
}
