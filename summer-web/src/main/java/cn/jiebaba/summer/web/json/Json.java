package cn.jiebaba.summer.web.json;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        write(sb, value);
        return sb.toString();
    }

    public static String toPretty(Object value) {
        return stringify(value);
    }

    /**
     * 按值的实际运行时类型分派序列化，将结果写入 {@code sb}：支持 null、{@link Optional}、
     * 布尔、数字、字符、枚举、字符串/时间、数组、集合、Map、Record 及普通对象。
     *
     * @param sb    目标缓冲区
     * @param value 待序列化的值
     */
    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Optional<?> opt) {
            write(sb, opt.orElse(null));
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
            writeArray(sb, value);
        } else if (value instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                write(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Record) {
            writeRecord(sb, value);
        } else {
            writeObject(sb, value);
        }
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

    private static void writeArray(StringBuilder sb, Object array) {
        sb.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(',');
            write(sb, Array.get(array, i));
        }
        sb.append(']');
    }

    private static void writeRecord(StringBuilder sb, Object record) {
        sb.append('{');
        RecordComponent[] components = record.getClass().getRecordComponents();
        boolean first = true;
        for (RecordComponent rc : components) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, rc.getName());
            sb.append(':');
            try {
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                write(sb, accessor.invoke(record));
            } catch (ReflectiveOperationException e) {
                sb.append("null");
            }
        }
        sb.append('}');
    }

    /**
     * 将普通对象序列化为 JSON 对象：先遍历无参 getter 推导属性名并去重，
     * 再补充尚未出现过的公开字段，逐个写出键值对。
     *
     * @param sb     目标缓冲区
     * @param object 待序列化对象
     */
    private static void writeObject(StringBuilder sb, Object object) {
        sb.append('{');
        boolean first = true;
        Set<String> seen = new LinkedHashSet<>();
        for (Method m : object.getClass().getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == void.class) continue;
            if (m.isSynthetic() || m.isBridge()) continue;
            if ("getClass".equals(name)) continue;
            String property = propertyNameFromGetter(name);
            if (property == null) continue;
            if (!seen.add(property)) continue;
            if (!first) sb.append(',');
            first = false;
            writeString(sb, property);
            sb.append(':');
            try {
                m.setAccessible(true);
                write(sb, m.invoke(object));
            } catch (ReflectiveOperationException e) {
                sb.append("null");
            }
        }
        for (Field f : collectFields(object.getClass())) {
            if (!seen.add(f.getName())) continue;
            if (!first) sb.append(',');
            first = false;
            writeString(sb, f.getName());
            sb.append(':');
            try {
                f.setAccessible(true);
                write(sb, f.get(object));
            } catch (IllegalAccessException e) {
                sb.append("null");
            }
        }
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

    /**
     * 将字符串转义后写入：对引号、反斜杠及控制字符做 JSON 转义，
     * 非 ASCII 字符按其原始字符输出。
     *
     * @param sb 目标缓冲区
     * @param s  待写入字符串
     */
    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
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
        sb.append('"');
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

    @SuppressWarnings("unchecked")
    public static <T> T parse(String json, Class<T> type) {
        return (T) bind(parse(json), type, type);
    }

    /**
     * 将 JSON 解析为泛型类型，例如 {@code List<User>}。使用 {@link TypeReference}
     * 捕获类型，或通过其他方式构造 {@link Type}。
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(String json, Type type) {
        return (T) bind(parse(json), erase(type), type);
    }

    public static <T> T parse(String json, TypeReference<T> typeRef) {
        return parse(json, typeRef.getType());
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
        if (rawType == java.time.temporal.TemporalAccessor.class || rawType.getName().startsWith("java.time")) {
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
        try {
            if (rawType.isRecord()) {
                return bindToRecord(map, rawType);
            }
            Constructor<?> ctor = rawType.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object bean = ctor.newInstance();
            for (Field f : collectFields(rawType)) {
                if (!map.containsKey(f.getName())) continue;
                Object fieldValue = bind(map.get(f.getName()), f.getType(), f.getGenericType());
                f.setAccessible(true);
                f.set(bean, fieldValue);
            }
            return bean;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot bind JSON to " + rawType.getName(), e);
        }
    }

    private static Object bindToRecord(Map<?, ?> map, Class<?> rawType) {
        RecordComponent[] components = rawType.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            Object raw = map.get(components[i].getName());
            args[i] = bind(raw, components[i].getType(), components[i].getGenericType());
        }
        try {
            Constructor<?> canonical = rawType.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            return canonical.newInstance(args);
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
