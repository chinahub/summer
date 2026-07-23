package cn.jiebaba.summer.ai.structured;

import cn.jiebaba.summer.core.json.Json;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于反射的 JSON Schema 生成器：将 Java 类型（record/bean/集合/数组/枚举/基本类型）
 * 转换为 JSON Schema（Draft 2020-12 风格），供结构化输出时约束模型返回格式。
 *
 * <p>字段采集规则与 {@link Json} 反序列化保持一致：record 取规范构造器分量，
 * bean 取全部非静态、非瞬态、非合成字段并沿父类链上溯。
 */
public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {
    }

    /**
     * 为指定类型生成 JSON Schema 文本。
     *
     * @param type 目标 Java 类型
     * @return JSON Schema 字符串
     */
    public static String generate(Type type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        build(type, schema);
        return Json.toPretty(schema);
    }

    /**
     * 递归将类型信息写入 schema 片段：基本类型映射为对应 JSON 类型，
     * 枚举附加可选值，集合/数组映射为 array，Map 映射为 object，其余按对象结构展开。
     *
     * @param type   目标 Java 类型
     * @param schema 待填充的 schema 片段
     */
    private static void build(Type type, Map<String, Object> schema) {
        Class<?> raw = rawClass(type);
        if (raw == boolean.class || raw == Boolean.class) {
            schema.put("type", "boolean");
        } else if (isInteger(raw)) {
            schema.put("type", "integer");
        } else if (isNumber(raw)) {
            schema.put("type", "number");
        } else if (isStringLike(raw)) {
            schema.put("type", "string");
        } else if (raw.isEnum()) {
            schema.put("type", "string");
            schema.put("enum", enumNames(raw));
        } else if (raw.isArray() || Collection.class.isAssignableFrom(raw)) {
            schema.put("type", "array");
            Map<String, Object> items = new LinkedHashMap<>();
            build(elementType(type, raw), items);
            schema.put("items", items);
        } else if (Map.class.isAssignableFrom(raw)) {
            schema.put("type", "object");
            Type valueType = mapValueType(type);
            if (valueType != null) {
                Map<String, Object> additional = new LinkedHashMap<>();
                build(valueType, additional);
                schema.put("additionalProperties", additional);
            }
        } else {
            buildObjectSchema(raw, schema);
        }
    }

    /**
     * 为 record 或 bean 构建 object schema：枚举属性、必填字段与各属性子 schema。
     *
     * @param raw    目标原始类
     * @param schema 待填充的 schema 片段
     */
    private static void buildObjectSchema(Class<?> raw, Map<String, Object> schema) {
        schema.put("type", "object");
        List<Property> properties = collectProperties(raw);
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Property property : properties) {
            Map<String, Object> sub = new LinkedHashMap<>();
            build(property.genericType, sub);
            props.put(property.name, sub);
            required.add(property.name);
        }
        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
    }

    /** record 分量或 bean 字段的统一描述。 */
    private record Property(String name, Type genericType) {
    }

    /**
     * 采集类型的可序列化属性：record 取分量，bean 取字段（与 Json 反序列化一致）。
     */
    private static List<Property> collectProperties(Class<?> raw) {
        List<Property> properties = new ArrayList<>();
        if (raw.isRecord()) {
            for (RecordComponent rc : raw.getRecordComponents()) {
                properties.add(new Property(rc.getName(), rc.getGenericType()));
            }
            return properties;
        }
        Class<?> current = raw;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (!Modifier.isStatic(mod) && !Modifier.isTransient(mod) && !field.isSynthetic()) {
                    properties.add(new Property(field.getName(), field.getGenericType()));
                }
            }
            current = current.getSuperclass();
        }
        return properties;
    }

    private static boolean isInteger(Class<?> raw) {
        return raw == byte.class || raw == short.class || raw == int.class || raw == long.class
                || raw == Byte.class || raw == Short.class || raw == Integer.class || raw == Long.class;
    }

    private static boolean isNumber(Class<?> raw) {
        return raw == float.class || raw == double.class
                || raw == Float.class || raw == Double.class;
    }

    private static boolean isStringLike(Class<?> raw) {
        return raw == String.class || raw == char.class || raw == Character.class
                || CharSequence.class.isAssignableFrom(raw)
                || TemporalAccessor.class.isAssignableFrom(raw)
                || Date.class.isAssignableFrom(raw)
                || raw == UUID.class;
    }

    private static List<String> enumNames(Class<?> enumType) {
        List<String> names = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        return Object.class;
    }

    /**
     * 提取数组或集合的元素类型；无法确定时回退到 Object。
     */
    private static Type elementType(Type type, Class<?> raw) {
        if (raw.isArray()) {
            return raw.getComponentType();
        }
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1) {
                return args[0];
            }
        }
        if (type instanceof GenericArrayType gat) {
            return gat.getGenericComponentType();
        }
        return Object.class;
    }

    /**
     * 提取 Map 的值类型（第二个泛型参数）；无法确定时返回 null。
     */
    private static Type mapValueType(Type type) {
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 2) {
                return args[1];
            }
        }
        return null;
    }
}
