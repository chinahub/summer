package cn.jiebaba.summer.data.metadata;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableField;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableLogic;
import cn.jiebaba.summer.data.annotation.TableName;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MetadataParser {

    private static final Map<Class<?>, TableInfo> CACHE = new ConcurrentHashMap<>();

    private MetadataParser() {}

    public static TableInfo parse(Class<?> entityType) {
        return CACHE.computeIfAbsent(entityType, MetadataParser::doParse);
    }

    private static TableInfo doParse(Class<?> entityType) {
        if (entityType.isRecord()) {
            throw new IllegalArgumentException("summer-data does not support record entities (use a class with fields): " + entityType.getName());
        }
        TableInfo info = new TableInfo();
        info.entityType(entityType);

        TableName tableName = entityType.getAnnotation(TableName.class);
        if (tableName != null) {
            info.tableName(tableName.value().isEmpty()
                    ? NamingUtils.toSnakeCase(entityType.getSimpleName()) : tableName.value());
            info.schema(tableName.schema());
        } else {
            info.tableName(NamingUtils.toSnakeCase(entityType.getSimpleName()));
        }

        for (Field field : collectFields(entityType)) {
            if (field.isSynthetic()) continue;
            TableField tf = field.getAnnotation(TableField.class);
            if (tf != null && !tf.exist()) continue;

            String column;
            if (tf != null && !tf.value().isEmpty()) {
                column = tf.value();
            } else {
                column = NamingUtils.toSnakeCase(field.getName());
            }

            TableId tableId = field.getAnnotation(TableId.class);
            TableLogic logic = field.getAnnotation(TableLogic.class);
            boolean isId = tableId != null;
            boolean insertable = tf == null || !"NEVER".equals(tf.insertStrategy());
            boolean updatable = tf == null || !"NEVER".equals(tf.updateStrategy());

            TableFieldInfo fi = new TableFieldInfo(field.getName(), column, field, isId,
                    insertable, updatable, logic != null,
                    logic != null ? logic.value() : "0",
                    logic != null ? logic.delval() : "1");
            info.addField(fi);

            if (isId) {
                info.idField(fi);
                info.idType(tableId.type());
                if (tableId.type() == IdType.AUTO) {
                    // auto increment: not inserted explicitly
                }
            }
            if (logic != null) {
                info.hasLogicDelete(true);
                info.logicDeleteField(fi);
            }
        }

        if (info.idField() == null && info.fields().stream().anyMatch(f -> "id".equals(f.property()))) {
            TableFieldInfo id = info.field("id");
            info.idField(id);
        }
        return info;
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!f.isSynthetic() && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }
}
