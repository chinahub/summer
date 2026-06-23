package cn.jiebaba.summer.data.metadata;

import java.lang.reflect.Field;

public class TableFieldInfo {
    private final String property;
    private final String column;
    private final Field field;
    private final Class<?> javaType;
    private final boolean isId;
    private final boolean insertable;
    private final boolean updatable;
    private final boolean logicDelete;
    private final String logicNotDeleteValue;
    private final String logicDeleteValue;

    public TableFieldInfo(String property, String column, Field field, boolean isId,
                          boolean insertable, boolean updatable, boolean logicDelete,
                          String logicNotDeleteValue, String logicDeleteValue) {
        this.property = property;
        this.column = column;
        this.field = field;
        this.javaType = field.getType();
        this.isId = isId;
        this.insertable = insertable;
        this.updatable = updatable;
        this.logicDelete = logicDelete;
        this.logicNotDeleteValue = logicNotDeleteValue;
        this.logicDeleteValue = logicDeleteValue;
        field.setAccessible(true);
    }

    public String property() { return property; }
    public String column() { return column; }
    public Field field() { return field; }
    public Class<?> javaType() { return javaType; }
    public boolean isId() { return isId; }
    public boolean insertable() { return insertable; }
    public boolean updatable() { return updatable; }
    public boolean isLogicDelete() { return logicDelete; }
    public String logicNotDeleteValue() { return logicNotDeleteValue; }
    public String logicDeleteValue() { return logicDeleteValue; }

    public Object getValue(Object entity) {
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read field " + property, e);
        }
    }

    public void setValue(Object entity, Object value) {
        try {
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write field " + property, e);
        }
    }
}
