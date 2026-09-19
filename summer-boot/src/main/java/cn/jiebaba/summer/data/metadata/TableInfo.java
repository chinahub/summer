package cn.jiebaba.summer.data.metadata;

import cn.jiebaba.summer.data.annotation.IdType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableInfo {
    private Class<?> entityType;
    private String tableName;
    private String schema;
    private TableFieldInfo idField;
    private final Map<String, TableFieldInfo> fieldMap = new LinkedHashMap<>();
    private final List<TableFieldInfo> fields = new ArrayList<>();
    private IdType idType = IdType.ASSIGN_ID;
    private boolean hasLogicDelete;
    private TableFieldInfo logicDeleteField;

    public Class<?> entityType() { return entityType; }
    public void entityType(Class<?> entityType) { this.entityType = entityType; }
    public String tableName() { return tableName; }
    public void tableName(String tableName) { this.tableName = tableName; }
    public String schema() { return schema; }
    public void schema(String schema) { this.schema = schema; }
    public TableFieldInfo idField() { return idField; }
    public void idField(TableFieldInfo idField) { this.idField = idField; }
    public List<TableFieldInfo> fields() { return fields; }
    public Map<String, TableFieldInfo> fieldMap() { return fieldMap; }
    public IdType idType() { return idType; }
    public void idType(IdType idType) { this.idType = idType; }
    public boolean hasLogicDelete() { return hasLogicDelete; }
    public void hasLogicDelete(boolean hasLogicDelete) { this.hasLogicDelete = hasLogicDelete; }
    public TableFieldInfo logicDeleteField() { return logicDeleteField; }
    public void logicDeleteField(TableFieldInfo logicDeleteField) { this.logicDeleteField = logicDeleteField; }

    public String qualifiedTableName() {
        if (schema == null || schema.isEmpty()) return tableName;
        return schema + "." + tableName;
    }

    public void addField(TableFieldInfo info) {
        fields.add(info);
        fieldMap.put(info.property(), info);
    }

    public TableFieldInfo field(String property) {
        return fieldMap.get(property);
    }

    public List<TableFieldInfo> insertFields() {
        List<TableFieldInfo> result = new ArrayList<>();
        for (TableFieldInfo f : fields) {
            if (f.insertable() && !f.isLogicDelete()) result.add(f);
        }
        return result;
    }

    public List<TableFieldInfo> updateFields() {
        List<TableFieldInfo> result = new ArrayList<>();
        for (TableFieldInfo f : fields) {
            if (f.updatable() && !f.isId() && !f.isLogicDelete()) result.add(f);
        }
        return result;
    }
}
