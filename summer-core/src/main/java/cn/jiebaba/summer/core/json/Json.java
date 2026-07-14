package cn.jiebaba.summer.core.json;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;

/**
 * 仅用 JDK 实现的极简 JSON 序列化器与解析器。
 * 支持 record、JavaBean（getter/字段）、Map、集合、数组、
 * 基本类型、枚举、字符串及常用 java.time 类型。
 */
public final class Json {
    private Json() {}

    // ---- 序列化 --------------------------------------------------------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, false);
        return sb.toString();
    }

    public static String toPretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, true);
        return sb.toString();
    }

    /**
     * 按值的实际运行时类型分派序列化，将结果写入 {@code sb}：支持 null、{@link Optional}、
     * 布尔、数字、字符、枚举、字符串/时间、数组、集合、Map、Record 及普通对象。
     *
     * @param sb    目标缓冲区
     * @param value 待序列化的值
     */
    private static void write(StringBuilder sb, Object value, int depth, boolean pretty) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Optional<?> opt) {
            write(sb, opt.orElse(null), depth, pretty);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Number n) {
            writeNumber(sb, n);
        } else if (value instanceof Character c) {
            writeString(sb, String.valueOf(c));
        } else if (value instanceof Enum<?> e) {
            writeString(sb, e.name());
        } else if (value instanceof CharSequence || value instanceof java.time.temporal.TemporalAccessor
                || value instanceof java.util.Date) {
            writeString(sb, value.toString());
        } else if (value.getClass().isArray()) {
            writeArray(sb, value, depth, pretty);
        } else if (value instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) sb.append(pretty ? ",\n" : ",");
                first = false;
                if (pretty) indent(sb, depth + 1);
                write(sb, item, depth + 1, pretty);
            }
            if (pretty && !first) { sb.append('\n'); indent(sb, depth); }
            sb.append(']');
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            if (pretty && !map.isEmpty()) sb.append('\n');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(pretty ? ",\n" : ",");
                first = false;
                if (pretty) indent(sb, depth + 1);
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, entry.getValue(), depth + 1, pretty);
            }
            if (pretty && !first) { sb.append('\n'); indent(sb, depth); }
            sb.append('}');
        } else if (value instanceof Record) {
            writeRecord(sb, value, depth, pretty);
        } else {
            writeObject(sb, value, depth, pretty);
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
        } else if (n instanceof Float || n instanceof Double) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(n.toString());
            }
        } else {
            sb.append(n.toString());
        }
    }

    private static void writeArray(StringBuilder sb, Object array, int depth, boolean pretty) {
        sb.append('[');
        int len = Array.getLength(array);
        if (pretty && len > 0) sb.append('\n');
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(pretty ? ",\n" : ",");
            if (pretty) indent(sb, depth + 1);
            write(sb, Array.get(array, i), depth + 1, pretty);
        }
        if (pretty && len > 0) { sb.append('\n'); indent(sb, depth); }
        sb.append(']');
    }

    private static void writeRecord(StringBuilder sb, Object record, int depth, boolean pretty) {
        sb.append('{');
        PropertyWriter[] props = writeSchema(record.getClass());
        if (pretty && props.length > 0) sb.append('\n');
        for (int i = 0; i < props.length; i++) {
            if (i > 0) sb.append(pretty ? ",\n" : ",");
            if (pretty) indent(sb, depth + 1);
            writeString(sb, props[i].name);
            sb.append(pretty ? ": " : ":");
            try {
                write(sb, props[i].read(record), depth + 1, pretty);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                sb.append("null");
            }
        }
        if (pretty && props.length > 0) { sb.append('\n'); indent(sb, depth); }
        sb.append('}');
    }

    /**
     * 将普通对象序列化为 JSON 对象：先遍历无参 getter 推导属性名并去重，
     * 再补充尚未出现过的公开字段，逐个写出键值对。
     *
     * @param sb     目标缓冲区
     * @param object 待序列化对象
     */
    private static void writeObject(StringBuilder sb, Object object, int depth, boolean pretty) {
        sb.append('{');
        PropertyWriter[] props = writeSchema(object.getClass());
        if (pretty && props.length > 0) sb.append('\n');
        for (int i = 0; i < props.length; i++) {
            if (i > 0) sb.append(pretty ? ",\n" : ",");
            if (pretty) indent(sb, depth + 1);
            writeString(sb, props[i].name);
            sb.append(pretty ? ": " : ":");
            try {
                write(sb, props[i].read(object), depth + 1, pretty);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                sb.append("null");
            }
        }
        if (pretty && props.length > 0) { sb.append('\n'); indent(sb, depth); }
        sb.append('}');
    }

    private static String propertyNameFromGetter(String name) {
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return null;
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!f.isSynthetic() && !java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && !java.lang.reflect.Modifier.isTransient(f.getModifiers())) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static final ConcurrentHashMap<Class<?>, PropertyWriter[]> WRITE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Object> READ_CACHE = new ConcurrentHashMap<>();

    /** 缓存的序列化属性写入器：名称 + 读取句柄，构造失败回退到 Method/Field。 */
    private static final class PropertyWriter {
        final String name;
        final MethodHandle getter;
        final Method method;
        final Field field;

        PropertyWriter(String name, MethodHandle getter, Method method, Field field) {
            this.name = name;
            this.getter = getter;
            this.method = method;
            this.field = field;
        }

        Object read(Object target) throws Throwable {
            if (getter != null) return (Object) getter.invokeExact(target);
            if (method != null) return method.invoke(target);
            return field.get(target);
        }
    }

    /** 缓存的反序列化字段绑定：名称/类型 + 设置句柄，失败回退到 Field。 */
    private static final class FieldBinding {
        final String name;
        final Class<?> type;
        final Type genericType;
        final MethodHandle setter;
        final Field field;

        FieldBinding(String name, Class<?> type, Type genericType, MethodHandle setter, Field field) {
            this.name = name;
            this.type = type;
            this.genericType = genericType;
            this.setter = setter;
            this.field = field;
        }
    }

    private static final class BeanSchema {
        final Constructor<?> ctor;
        final FieldBinding[] fields;
        final Map<String, FieldBinding> byName;
        BeanSchema(Constructor<?> ctor, FieldBinding[] fields, Map<String, FieldBinding> byName) {
            this.ctor = ctor; this.fields = fields; this.byName = byName;
        }
    }

    private static final class ComponentBinding {
        final String name;
        final Class<?> type;
        final Type genericType;
        ComponentBinding(String name, Class<?> type, Type genericType) {
            this.name = name; this.type = type; this.genericType = genericType;
        }
    }

    private static final class RecordSchema {
        final Constructor<?> canonical;
        final ComponentBinding[] components;
        final Map<String, Integer> byIndex;
        RecordSchema(Constructor<?> canonical, ComponentBinding[] components, Map<String, Integer> byIndex) {
            this.canonical = canonical; this.components = components; this.byIndex = byIndex;
        }
    }

    /** 取得类的序列化属性写入器数组（首次构建后缓存）。 */
    private static PropertyWriter[] writeSchema(Class<?> type) {
        return WRITE_CACHE.computeIfAbsent(type, Json::buildWriteSchema);
    }

    /**
     * 构建序列化属性写入器：record 取访问器；普通对象先取无参 getter 再补未出现的字段，
     * 读取句柄优先用 MethodHandle，构造失败回退到 Method/Field。
     */
    private static PropertyWriter[] buildWriteSchema(Class<?> type) {
        List<PropertyWriter> writers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                if (!seen.add(rc.getName())) continue;
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                writers.add(new PropertyWriter(rc.getName(), unreflectGetter(accessor), accessor, null));
            }
        } else {
            for (Method m : type.getMethods()) {
                String name = m.getName();
                if (m.getParameterCount() != 0) continue;
                if (m.getReturnType() == void.class) continue;
                if (m.isSynthetic() || m.isBridge()) continue;
                if ("getClass".equals(name)) continue;
                String property = propertyNameFromGetter(name);
                if (property == null) continue;
                if (!seen.add(property)) continue;
                m.setAccessible(true);
                writers.add(new PropertyWriter(property, unreflectGetter(m), m, null));
            }
            for (Field f : collectFields(type)) {
                if (!seen.add(f.getName())) continue;
                f.setAccessible(true);
                writers.add(new PropertyWriter(f.getName(), unreflectGetter(f), null, f));
            }
        }
        return writers.toArray(new PropertyWriter[0]);
    }

    /** 将方法反射为 (Object)Object 读取句柄；失败返回 null 走 Method 回退。 */
    private static MethodHandle unreflectGetter(Method m) {
        try {
            return MethodHandles.lookup().unreflect(m).asType(MethodType.methodType(Object.class, Object.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 将字段反射为 (Object)Object 读取句柄；失败返回 null 走 Field 回退。 */
    private static MethodHandle unreflectGetter(Field f) {
        try {
            return MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class, Object.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 构建反序列化 schema：record 返回 RecordSchema，否则返回 BeanSchema。 */
    private static Object buildReadSchema(Class<?> type) {
        return type.isRecord() ? buildRecordSchema(type) : buildBeanSchema(type);
    }

    private static BeanSchema buildBeanSchema(Class<?> type) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            List<FieldBinding> fields = new ArrayList<>();
            for (Field f : collectFields(type)) {
                f.setAccessible(true);
                fields.add(new FieldBinding(f.getName(), f.getType(), f.getGenericType(), unreflectSetter(f), f));
            }
            FieldBinding[] arr = fields.toArray(new FieldBinding[0]);
            Map<String, FieldBinding> byName = new HashMap<>(arr.length * 2);
            for (FieldBinding fb : arr) byName.put(fb.name, fb);
            return new BeanSchema(ctor, arr, byName);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No no-arg constructor for " + type.getName(), e);
        }
    }

    private static RecordSchema buildRecordSchema(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        ComponentBinding[] bindings = new ComponentBinding[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            bindings[i] = new ComponentBinding(components[i].getName(), components[i].getType(), components[i].getGenericType());
        }
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            Map<String, Integer> byIndex = new HashMap<>(bindings.length * 2);
            for (int i = 0; i < bindings.length; i++) byIndex.put(bindings[i].name, i);
            return new RecordSchema(canonical, bindings, byIndex);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No canonical constructor for record " + type.getName(), e);
        }
    }

    /** 将字段反射为 (Object,Object)V 设置句柄；失败返回 null 走 Field 回退。 */
    private static MethodHandle unreflectSetter(Field f) {
        try {
            return MethodHandles.lookup().unreflectSetter(f)
                    .asType(MethodType.methodType(void.class, Object.class, Object.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 直接将对象序列化为 UTF-8 字节，避免中间 String 拷贝。 */
    public static byte[] toUtf8Bytes(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, false);
        java.nio.ByteBuffer bb = java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(sb));
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        return out;
    }

    /** 将字符串以 JSON 转义形式包裹双引号返回，例如 {@code quote("a\nb") -> "\"a\\nb\""}。 */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        escapeInto(sb, value);
        sb.append('"');
        return sb.toString();
    }

    /** 将字符串按 JSON 规则转义（不含外层引号）后返回；{@code null} 原样返回。 */
    public static String escape(String value) {
        if (value == null) return null;
        StringBuilder sb = new StringBuilder(value.length());
        escapeInto(sb, value);
        return sb.toString();
    }

    /**
     * 将字符串转义后写入：对引号、反斜杠及控制字符做 JSON 转义，
     * 非 ASCII 字符按其原始字符输出。
     *
     * @param sb 目标缓冲区
     * @param s  待写入字符串
     */
    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        escapeInto(sb, s);
        sb.append('"');
    }

    /** 将字符串的每个字符按 JSON 转义规则追加到缓冲：引号、反斜杠、控制字符转义，非 ASCII 原样输出。 */
    private static void escapeInto(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    // ---- 解析 --------------------------------------------------------------

    public static Object parse(String json) {
        Parser p = new Parser(json);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos < p.src.length) {
            throw new IllegalArgumentException("Unexpected trailing content at position " + p.pos);
        }
        return value;
    }

    public static <T> T parse(String json, Class<T> type) {
        return read(json.getBytes(StandardCharsets.UTF_8), type);
    }

    /**
     * 将 JSON 解析为泛型类型，例如 {@code List<User>}。使用 {@link TypeReference}
     * 捕获类型，或通过其他方式构造 {@link Type}。
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(String json, Type type) {
        return (T) read(json.getBytes(StandardCharsets.UTF_8), type);
    }

    public static <T> T parse(String json, TypeReference<T> typeRef) {
        return parse(json, typeRef.getType());
    }

    // ---- streaming-bind 反序列化（按 schema 直连，无中间通用树）----------------

    /**
     * 直接从 UTF-8 字节流式读取并按目标类型绑定：解析器边读 token 边按 schema 构造目标对象，
     * 不构建中间 Map/List 通用树，减少分配与一次遍历。用于请求体热路径。
     *
     * @param json UTF-8 JSON 字节
     * @param type 目标类型（可为泛型 {@link Type}）
     * @return 绑定后的对象
     */
    public static Object read(byte[] json, Type type) {
        if (json == null || json.length == 0) {
            throw new IllegalArgumentException("empty JSON");
        }
        JsonReader r = new JsonReader(json);
        Object value = bindStreaming(r, erase(type), type);
        r.checkTrailing();
        return value;
    }

    /**
     * 直接从 UTF-8 字节流式读取并按目标类绑定，等价于 {@code read(json, (Type) type)}。
     *
     * @param json UTF-8 JSON 字节
     * @param type 目标类
     * @param <T>  目标类型
     * @return 绑定后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T read(byte[] json, Class<T> type) {
        return (T) read(json, (Type) type);
    }

    /**
     * 流式绑定的类型分派：依据当前 token 与目标类型直接读取并构造对应值。处理 null、字符串、
     * 数字、布尔、对象（Bean/Record/Map）、数组/集合，以及字符串到数值/布尔的弱转换；
     * 类型不匹配时回退为通用字面量，保持与树式 bind 一致的行为。
     *
     * @param r           字节读取器
     * @param rawType     目标原始类型
     * @param genericType 目标泛型类型
     * @return 绑定后的值
     */
    @SuppressWarnings("unchecked")
    private static Object bindStreaming(JsonReader r, Class<?> rawType, Type genericType) {
        r.skipWs();
        int c = r.peek();
        if (c == 'n') { r.nextNull(); return defaultValue(rawType); }
        if (rawType == Object.class) return r.nextLiteral();
        if (rawType == String.class || rawType == CharSequence.class) {
            return c == '"' ? r.nextString() : r.nextLiteral().toString();
        }
        if (rawType.isEnum()) {
            String s = c == '"' ? r.nextString() : r.nextLiteral().toString();
            return Enum.valueOf((Class<? extends Enum>) rawType, s);
        }
        if (c == '"') {
            String s = r.nextString();
            if (rawType == char.class || rawType == Character.class) return s.isEmpty() ? '\0' : s.charAt(0);
            if (isNumeric(rawType)) return coerceNumber(Double.valueOf(s), rawType);
            if (rawType == boolean.class || rawType == Boolean.class) return Boolean.parseBoolean(s);
            if (isTemporal(rawType)) return parseTemporal(rawType, s);
            return s;
        }
        if (c == '{') {
            if (Map.class.isAssignableFrom(rawType)) return bindMap(r, rawType, genericType);
            if (Collection.class.isAssignableFrom(rawType) || rawType.isArray()) return r.nextLiteral();
            return rawType.isRecord() ? bindRecord(r, rawType) : bindBean(r, rawType);
        }
        if (c == '[') {
            if (rawType.isArray()) return bindArray(r, rawType.getComponentType());
            if (Collection.class.isAssignableFrom(rawType)) return bindCollection(r, rawType, genericType);
            return r.nextLiteral();
        }
        if (c == 't' || c == 'f') return r.nextBoolean();
        Number num = r.nextNumber();
        if (isNumeric(rawType)) return coerceNumber(num, rawType);
        return num;
    }

    /** 流式绑定到 Bean：按字段名查 schema 的 byName 映射，命中则递归绑定字段值，未命中则跳过该值。 */
    private static Object bindBean(JsonReader r, Class<?> rawType) {
        try {
            BeanSchema schema = (BeanSchema) READ_CACHE.computeIfAbsent(rawType, Json::buildReadSchema);
            Object bean = schema.ctor.newInstance();
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                FieldBinding fb = schema.byName.get(name);
                if (fb == null) { r.skipValue(); continue; }
                Object fv = bindStreaming(r, fb.type, fb.genericType);
                if (fb.setter != null) {
                    fb.setter.invokeExact(bean, fv);
                } else {
                    fb.field.set(bean, fv);
                }
            }
            r.endObject();
            return bean;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot bind JSON to " + rawType.getName(), t);
        }
    }

    /** 流式绑定到 Record：先以类型默认值填充各分量，再按名查 byIndex 覆盖命中的分量，最后调用规范构造器。 */
    private static Object bindRecord(JsonReader r, Class<?> rawType) {
        try {
            RecordSchema schema = (RecordSchema) READ_CACHE.computeIfAbsent(rawType, Json::buildReadSchema);
            ComponentBinding[] comps = schema.components;
            Object[] args = new Object[comps.length];
            for (int i = 0; i < comps.length; i++) args[i] = defaultValue(comps[i].type);
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                Integer idx = schema.byIndex.get(name);
                if (idx == null) { r.skipValue(); continue; }
                ComponentBinding cb = comps[idx];
                args[idx] = bindStreaming(r, cb.type, cb.genericType);
            }
            r.endObject();
            return schema.canonical.newInstance(args);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot bind JSON to record " + rawType.getName(), t);
        }
    }

    /** 流式绑定到数组：先收集到临时 List（长度未知），再拷贝为目标类型数组。 */
    private static Object bindArray(JsonReader r, Class<?> componentType) {
        r.beginArray();
        List<Object> tmp = new ArrayList<>();
        while (r.hasNext()) tmp.add(bindStreaming(r, componentType, componentType));
        r.endArray();
        Object array = Array.newInstance(componentType, tmp.size());
        for (int i = 0; i < tmp.size(); i++) Array.set(array, i, tmp.get(i));
        return array;
    }

    /** 流式绑定到集合：按泛型元素类型逐个递归绑定并加入目标集合。 */
    @SuppressWarnings("unchecked")
    private static Object bindCollection(JsonReader r, Class<?> rawType, Type genericType) {
        Collection<Object> result = newCollection(rawType);
        Type[] ta = typeArguments(genericType);
        Type elemGen = ta.length > 0 ? ta[0] : Object.class;
        Class<?> elemType = erase(elemGen);
        r.beginArray();
        while (r.hasNext()) result.add(bindStreaming(r, elemType, elemGen));
        r.endArray();
        return result;
    }

    /** 流式绑定到 Map：键按字符串/键类型绑定，值按泛型值类型递归绑定。 */
    @SuppressWarnings("unchecked")
    private static Object bindMap(JsonReader r, Class<?> rawType, Type genericType) {
        Map<Object, Object> result = newMap(rawType);
        Type[] ta = typeArguments(genericType);
        Class<?> keyType = ta.length > 0 ? erase(ta[0]) : String.class;
        Class<?> valueType = ta.length > 1 ? erase(ta[1]) : Object.class;
        Type valueGen = ta.length > 1 ? ta[1] : valueType;
        r.beginObject();
        while (r.hasNext()) {
            String name = r.nextName();
            Object key = bind(name, keyType, keyType);
            result.put(key, bindStreaming(r, valueType, valueGen));
        }
        r.endObject();
        return result;
    }

    /**
     * 将解析得到的值绑定到目标类型：处理基本类型、枚举、数字窄化、布尔、字符、
     * java.time 类型，以及 Map 到 Bean、Map 到 Map、List 到数组/集合的转换。
     *
     * @param value        解析得到的值
     * @param rawType      目标原始类型
     * @param genericType  目标泛型类型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    public static Object bind(Object value, Class<?> rawType, Type genericType) {
        if (value == null) return defaultValue(rawType);
        if (rawType == Object.class) return value;
        if (rawType == String.class || rawType == CharSequence.class) return value.toString();
        if (rawType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawType, value.toString());
        }
        if (value instanceof Number num && isNumeric(rawType)) {
            return coerceNumber(num, rawType);
        }
        if (value instanceof Boolean b && (rawType == boolean.class || rawType == Boolean.class)) {
            return b;
        }
        if (value instanceof String s && (rawType == char.class || rawType == Character.class)) {
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (isTemporal(rawType)) {
            return parseTemporal(rawType, value.toString());
        }
        if (value instanceof Map<?, ?> map && !isCollectionOrMap(rawType)) {
            return bindToBean(map, rawType);
        }
        if (value instanceof Map<?, ?> map && Map.class.isAssignableFrom(rawType)) {
            return bindToMap(map, rawType, genericType);
        }
        if ((value instanceof List<?> list) && (rawType.isArray() || Collection.class.isAssignableFrom(rawType))) {
            return bindToCollection(list, rawType, genericType);
        }
        if (value instanceof String s && isNumeric(rawType)) {
            return coerceNumber(Double.valueOf(s), rawType);
        }
        if (value instanceof String s && (rawType == boolean.class || rawType == Boolean.class)) {
            return Boolean.parseBoolean(s);
        }
        return value;
    }

    private static Object bindToBean(Map<?, ?> map, Class<?> rawType) {
        if (rawType.isRecord()) {
            return bindToRecord(map, rawType);
        }
        try {
            BeanSchema schema = (BeanSchema) READ_CACHE.computeIfAbsent(rawType, Json::buildReadSchema);
            Object bean = schema.ctor.newInstance();
            for (FieldBinding fb : schema.fields) {
                if (!map.containsKey(fb.name)) continue;
                Object fieldValue = bind(map.get(fb.name), fb.type, fb.genericType);
                if (fb.setter != null) {
                    fb.setter.invokeExact(bean, fieldValue);
                } else {
                    fb.field.set(bean, fieldValue);
                }
            }
            return bean;
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot bind JSON to " + rawType.getName(), t);
        }
    }

    private static Object bindToRecord(Map<?, ?> map, Class<?> rawType) {
        try {
            RecordSchema schema = (RecordSchema) READ_CACHE.computeIfAbsent(rawType, Json::buildReadSchema);
            Object[] args = new Object[schema.components.length];
            for (int i = 0; i < schema.components.length; i++) {
                ComponentBinding cb = schema.components[i];
                args[i] = bind(map.get(cb.name), cb.type, cb.genericType);
            }
            return schema.canonical.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot construct record " + rawType.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object bindToMap(Map<?, ?> map, Class<?> rawType, Type genericType) {
        Map<Object, Object> result = newMap(rawType);
        Type[] typeArgs = typeArguments(genericType);
        Class<?> keyType = typeArgs.length > 0 ? erase(typeArgs[0]) : String.class;
        Class<?> valueType = typeArgs.length > 1 ? erase(typeArgs[1]) : Object.class;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = bind(entry.getKey() == null ? null : entry.getKey().toString(), keyType, keyType);
            Object value = bind(entry.getValue(), valueType, typeArgs.length > 1 ? typeArgs[1] : valueType);
            result.put(key, value);
        }
        return result;
    }

    private static Object bindToCollection(List<?> list, Class<?> rawType, Type genericType) {
        if (rawType.isArray()) {
            Class<?> component = rawType.getComponentType();
            Object array = Array.newInstance(component, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, bind(list.get(i), component, component));
            }
            return array;
        }
        Collection<Object> result = newCollection(rawType);
        Type[] typeArgs = typeArguments(genericType);
        Class<?> elementType = typeArgs.length > 0 ? erase(typeArgs[0]) : Object.class;
        for (Object item : list) {
            result.add(bind(item, elementType, typeArgs.length > 0 ? typeArgs[0] : elementType));
        }
        return result;
    }

    private static Object parseTemporal(Class<?> type, String text) {
        try {
            Method parse = type.getMethod("parse", CharSequence.class);
            return parse.invoke(null, text);
        } catch (ReflectiveOperationException e) {
            return text;
        }
    }

    private static boolean isTemporal(Class<?> type) {
        return type == java.time.temporal.TemporalAccessor.class || type.getName().startsWith("java.time");
    }

    private static boolean isNumeric(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class;
    }

    /**
     * 将数字窄化转换为目标数值类型；对 int/short/byte 做范围校验，超界时抛出异常，
     * 避免大 Long 绑定到 Integer 字段时被静默截断。
     *
     * @param num  原始数字
     * @param type 目标数值类型
     * @return 转换后的数字
     * @throws IllegalArgumentException 当窄化转换超出目标类型范围时抛出
     */
    private static Object coerceNumber(Number num, Class<?> type) {
        // 窄化转换必须校验范围；否则绑定到 Integer 字段时，超过 Integer.MAX_VALUE 的 Long 型 id
        // 会被静默截断（见 #2）。
        if (type == int.class || type == Integer.class) {
            long v = num.longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Number " + v + " out of int range");
            }
            return (int) v;
        }
        if (type == long.class || type == Long.class) return num.longValue();
        if (type == short.class || type == Short.class) {
            long v = num.longValue();
            if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Number " + v + " out of short range");
            }
            return (short) v;
        }
        if (type == byte.class || type == Byte.class) {
            long v = num.longValue();
            if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Number " + v + " out of byte range");
            }
            return (byte) v;
        }
        if (type == double.class || type == Double.class) return num.doubleValue();
        if (type == float.class || type == Float.class) return num.floatValue();
        return num;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == char.class) return '\0';
        return null;
    }

    private static Type[] typeArguments(Type type) {
        if (type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments();
        }
        return new Type[0];
    }

    @SuppressWarnings("unchecked")
    private static Class<?> erase(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();
        return Object.class;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> newMap(Class<?> rawType) {
        if (rawType.isInterface() || java.lang.reflect.Modifier.isAbstract(rawType.getModifiers())) {
            return new LinkedHashMap<>();
        }
        try {
            return (Map<Object, Object>) rawType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> newCollection(Class<?> rawType) {
        if (rawType.isInterface() || java.lang.reflect.Modifier.isAbstract(rawType.getModifiers())) {
            if (Set.class.isAssignableFrom(rawType)) return new LinkedHashSet<>();
            return new ArrayList<>();
        }
        try {
            return (Collection<Object>) rawType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return new ArrayList<>();
        }
    }

    private static boolean isCollectionOrMap(Class<?> type) {
        return Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
    }

    private static final class Parser {
        final char[] src;
        int pos;

        Parser(String json) {
            this.src = json.toCharArray();
            this.pos = 0;
        }

        Object readValue() {
            skipWhitespace();
            if (pos >= src.length) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = src[pos];
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw new IllegalArgumentException("Expected ',' or '}' at " + (pos - 1));
            }
            return map;
        }

        List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == ']') break;
                throw new IllegalArgumentException("Expected ',' or ']' at " + (pos - 1));
            }
            return list;
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length) {
                char c = src[pos++];
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char esc = src[pos++];
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = new String(src, pos, 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length && Character.isDigit(src[pos])) pos++;
            boolean floating = false;
            if (pos < src.length && src[pos] == '.') {
                floating = true;
                pos++;
                while (pos < src.length && Character.isDigit(src[pos])) pos++;
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                floating = true;
                pos++;
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++;
                while (pos < src.length && Character.isDigit(src[pos])) pos++;
            }
            String token = new String(src, start, pos - start);
            return floating ? Double.valueOf(token) : Long.valueOf(token);
        }

        Boolean readBoolean() {
            if (match("true")) return Boolean.TRUE;
            if (match("false")) return Boolean.FALSE;
            throw new IllegalArgumentException("Invalid literal at " + pos);
        }

        Object readNull() {
            if (match("null")) return null;
            throw new IllegalArgumentException("Invalid literal at " + pos);
        }

        boolean match(String word) {
            if (pos + word.length() > src.length) return false;
            for (int i = 0; i < word.length(); i++) {
                if (src[pos + i] != word.charAt(i)) return false;
            }
            pos += word.length();
            return true;
        }

        void skipWhitespace() {
            while (pos < src.length && Character.isWhitespace(src[pos])) pos++;
        }

        char peek() {
            return pos < src.length ? src[pos] : '\0';
        }

        char next() {
            return pos < src.length ? src[pos++] : '\0';
        }

        void expect(char c) {
            if (pos >= src.length || src[pos] != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            }
            pos++;
        }
    }
}
