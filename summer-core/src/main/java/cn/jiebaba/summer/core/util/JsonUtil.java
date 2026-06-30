package cn.jiebaba.summer.core.util;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * JSON helpers inspired by {@code cn.hutool.json.JSONUtil}.
 *
 * <p>A self-contained JSON serializer/parser built only on the JDK: supports records,
 * JavaBeans (getters/fields), maps, collections, arrays, primitives, enums, {@link Optional},
 * {@code java.time} types and {@link java.util.Date}. No third-party dependency.
 */
public final class JsonUtil {

    private JsonUtil() {}

    // ---- serialization -------------------------------------------------------

    public static String toJsonStr(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, false);
        return sb.toString();
    }

    public static String toJsonPrettyStr(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, true);
        return sb.toString();
    }

    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        writeString(sb, value);
        return sb.toString();
    }

    public static String escape(String value) {
        if (value == null) return null;
        StringBuilder sb = new StringBuilder(value.length());
        escapeInto(sb, value);
        return sb.toString();
    }

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
            if (pretty && it.iterator().hasNext()) sb.append('\n');
            boolean first = true;
            for (Object item : it) {
                if (!first) { sb.append(pretty ? ",\n" : ","); }
                first = false;
                if (pretty) indent(sb, depth + 1);
                write(sb, item, depth + 1, pretty);
            }
            if (pretty && !first) { sb.append('\n'); indent(sb, depth); }
            sb.append(']');
        } else if (value instanceof Map<?, ?> map) {
            writeMap(sb, map, depth, pretty);
        } else if (value instanceof Record) {
            writeRecord(sb, value, depth, pretty);
        } else {
            writeObject(sb, value, depth, pretty);
        }
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) { sb.append("null"); return; }
        if (n instanceof Float || n instanceof Double) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) sb.append((long) d);
            else sb.append(n.toString());
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

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int depth, boolean pretty) {
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
    }

    private static void writeRecord(StringBuilder sb, Object record, int depth, boolean pretty) {
        sb.append('{');
        RecordComponent[] components = record.getClass().getRecordComponents();
        if (pretty && components.length > 0) sb.append('\n');
        boolean first = true;
        for (RecordComponent rc : components) {
            if (!first) sb.append(pretty ? ",\n" : ",");
            first = false;
            if (pretty) indent(sb, depth + 1);
            writeString(sb, rc.getName());
            sb.append(pretty ? ": " : ":");
            try {
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                write(sb, accessor.invoke(record), depth + 1, pretty);
            } catch (ReflectiveOperationException e) {
                sb.append("null");
            }
        }
        if (pretty && !first) { sb.append('\n'); indent(sb, depth); }
        sb.append('}');
    }

    private static void writeObject(StringBuilder sb, Object object, int depth, boolean pretty) {
        sb.append('{');
        boolean first = true;
        Set<String> seen = new LinkedHashSet<>();
        for (Method m : object.getClass().getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) continue;
            if (m.isSynthetic() || m.isBridge() || "getClass".equals(name)) continue;
            String property = propertyNameFromGetter(name);
            if (property == null || !seen.add(property)) continue;
            if (!first) sb.append(pretty ? ",\n" : ",");
            first = false;
            if (pretty) indent(sb, depth + 1);
            writeString(sb, property);
            sb.append(pretty ? ": " : ":");
            try { m.setAccessible(true); write(sb, m.invoke(object), depth + 1, pretty); }
            catch (ReflectiveOperationException e) { sb.append("null"); }
        }
        for (Field f : collectFields(object.getClass())) {
            if (!seen.add(f.getName())) continue;
            if (!first) sb.append(pretty ? ",\n" : ",");
            first = false;
            if (pretty) indent(sb, depth + 1);
            writeString(sb, f.getName());
            sb.append(pretty ? ": " : ":");
            try { f.setAccessible(true); write(sb, f.get(object), depth + 1, pretty); }
            catch (IllegalAccessException e) { sb.append("null"); }
        }
        if (pretty && !first) { sb.append('\n'); indent(sb, depth); }
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
                if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        escapeInto(sb, s);
        sb.append('"');
    }

    private static void escapeInto(StringBuilder sb, String s) {
        if (s == null) return;
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
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    public static boolean isJson(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim();
        char first = t.charAt(0);
        return (first == '{' || first == '[') && tryParse(t) != null;
    }

    private static Object tryParse(String t) {
        try { return parse(t); } catch (Exception e) { return null; }
    }
    // ---- parsing -------------------------------------------------------------

    public static Object parse(String json) {
        if (json == null) return null;
        Parser p = new Parser(json);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos < p.src.length) {
            throw new IllegalArgumentException("Unexpected trailing content at position " + p.pos);
        }
        return value;
    }

    public static JSONObject parseObj(String json) {
        Object v = parse(json);
        if (v instanceof JSONObject jo) return jo;
        if (v instanceof Map<?, ?> map) {
            JSONObject jo = new JSONObject();
            for (Map.Entry<?, ?> e : map.entrySet()) jo.put(String.valueOf(e.getKey()), e.getValue());
            return jo;
        }
        throw new IllegalArgumentException("JSON is not an object: " + json);
    }

    public static JSONArray parseArray(String json) {
        Object v = parse(json);
        if (v instanceof JSONArray ja) return ja;
        if (v instanceof List<?> list) { JSONArray ja = new JSONArray(); ja.addAll(list); return ja; }
        throw new IllegalArgumentException("JSON is not an array: " + json);
    }

    @SuppressWarnings("unchecked")
    public static <T> T toBean(String json, Class<T> clazz) {
        return (T) bind(parse(json), clazz, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T toBean(String json, Type type) {
        return (T) bind(parse(json), erase(type), type);
    }

    public static <T> List<T> toList(String json, Class<T> elementType) {
        Object v = parse(json);
        if (!(v instanceof List<?> list)) throw new IllegalArgumentException("JSON is not an array: " + json);
        List<T> result = new ArrayList<>(list.size());
        for (Object item : list) result.add((T) bind(item, elementType, elementType));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object bind(Object value, Class<?> rawType, Type genericType) {
        if (value == null) return defaultValue(rawType);
        if (rawType == Object.class) return value;
        if (rawType == String.class || rawType == CharSequence.class) return value.toString();
        if (rawType.isEnum()) return Enum.valueOf((Class<? extends Enum>) rawType, value.toString());
        if (value instanceof Number num && isNumeric(rawType)) return coerceNumber(num, rawType);
        if (value instanceof Boolean b && (rawType == boolean.class || rawType == Boolean.class)) return b;
        if (value instanceof String s && (rawType == char.class || rawType == Character.class)) {
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (rawType == java.time.temporal.TemporalAccessor.class || rawType.getName().startsWith("java.time")) {
            return parseTemporal(rawType, value.toString());
        }
        if (value instanceof Map<?, ?> map && !isCollectionOrMap(rawType)) return bindToBean(map, rawType);
        if (value instanceof Map<?, ?> map && Map.class.isAssignableFrom(rawType)) return bindToMap(map, rawType, genericType);
        if (value instanceof List<?> list && (rawType.isArray() || Collection.class.isAssignableFrom(rawType))) {
            return bindToCollection(list, rawType, genericType);
        }
        if (value instanceof String s && isNumeric(rawType)) return coerceNumber(Double.valueOf(s), rawType);
        if (value instanceof String s && (rawType == boolean.class || rawType == Boolean.class)) return Boolean.parseBoolean(s);
        if (value instanceof JSONObject jo && rawType == JSONObject.class) return jo;
        if (value instanceof JSONArray ja && rawType == JSONArray.class) return ja;
        return value;
    }

    private static Object bindToBean(Map<?, ?> map, Class<?> rawType) {
        try {
            if (rawType.isRecord()) return bindToRecord(map, rawType);
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
            args[i] = bind(map.get(components[i].getName()), components[i].getType(), components[i].getGenericType());
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
        Type valueType = typeArgs.length > 1 ? typeArgs[1] : Object.class;
        Class<?> valueRaw = erase(valueType);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = bind(entry.getKey() == null ? null : entry.getKey().toString(), keyType, keyType);
            result.put(key, bind(entry.getValue(), valueRaw, valueType));
        }
        return result;
    }

    private static Object bindToCollection(List<?> list, Class<?> rawType, Type genericType) {
        if (rawType.isArray()) {
            Class<?> component = rawType.getComponentType();
            Object array = Array.newInstance(component, list.size());
            for (int i = 0; i < list.size(); i++) Array.set(array, i, bind(list.get(i), component, component));
            return array;
        }
        Collection<Object> result = newCollection(rawType);
        Type[] typeArgs = typeArguments(genericType);
        Class<?> elementType = typeArgs.length > 0 ? erase(typeArgs[0]) : Object.class;
        Type elementTypeT = typeArgs.length > 0 ? typeArgs[0] : elementType;
        for (Object item : list) result.add(bind(item, elementType, elementTypeT));
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
        return type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class
                || type == double.class || type == Double.class || type == float.class || type == Float.class;
    }

    private static Object coerceNumber(Number num, Class<?> type) {
        if (type == int.class || type == Integer.class) {
            long v = num.longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) throw new IllegalArgumentException("Number " + v + " out of int range");
            return (int) v;
        }
        if (type == long.class || type == Long.class) return num.longValue();
        if (type == short.class || type == Short.class) {
            long v = num.longValue();
            if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) throw new IllegalArgumentException("Number " + v + " out of short range");
            return (short) v;
        }
        if (type == byte.class || type == Byte.class) {
            long v = num.longValue();
            if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) throw new IllegalArgumentException("Number " + v + " out of byte range");
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
        return type instanceof ParameterizedType pt ? pt.getActualTypeArguments() : new Type[0];
    }

    @SuppressWarnings("unchecked")
    private static Class<?> erase(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();
        return Object.class;
    }

    private static boolean isCollectionOrMap(Class<?> type) {
        return Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> newMap(Class<?> rawType) {
        if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) return new LinkedHashMap<>();
        try { return (Map<Object, Object>) rawType.getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { return new LinkedHashMap<>(); }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> newCollection(Class<?> rawType) {
        if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
            return Set.class.isAssignableFrom(rawType) ? new LinkedHashSet<>() : new ArrayList<>();
        }
        try { return (Collection<Object>) rawType.getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { return new ArrayList<>(); }
    }
    private static final class Parser {
        private final char[] src;
        private int pos;

        Parser(String json) { this.src = json.toCharArray(); }

        Object readValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        JSONObject readObject() {
            expect('{');
            JSONObject map = new JSONObject();
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                map.put(key, readValue());
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw new IllegalArgumentException("Expected ',' or '}' at " + (pos - 1));
            }
            return map;
        }

        JSONArray readArray() {
            expect('[');
            JSONArray list = new JSONArray();
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
                        case 'u' -> { String hex = new String(src, pos, 4); sb.append((char) Integer.parseInt(hex, 16)); pos += 4; }
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
                floating = true; pos++;
                while (pos < src.length && Character.isDigit(src[pos])) pos++;
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                floating = true; pos++;
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
            for (int i = 0; i < word.length(); i++) if (src[pos + i] != word.charAt(i)) return false;
            pos += word.length();
            return true;
        }

        void skipWhitespace() { while (pos < src.length && Character.isWhitespace(src[pos])) pos++; }
        char peek() { return pos < src.length ? src[pos] : '\0'; }
        char next() { return pos < src.length ? src[pos++] : '\0'; }
        void expect(char c) {
            if (pos >= src.length || src[pos] != c) throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            pos++;
        }
    }

    /** A JSON object backed by a {@link LinkedHashMap} with typed accessors (hutool-style). */
    public static class JSONObject extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;

        public String getStr(String key) { Object v = get(key); return v == null ? null : v.toString(); }
        public Integer getInt(String key) { Object v = get(key); return v == null ? null : ((Number) v).intValue(); }
        public Long getLong(String key) { Object v = get(key); return v == null ? null : ((Number) v).longValue(); }
        public Double getDouble(String key) { Object v = get(key); return v == null ? null : ((Number) v).doubleValue(); }
        public Boolean getBool(String key) { Object v = get(key); return v == null ? null : Boolean.valueOf(v.toString()); }
        public JSONObject getJSONObject(String key) { Object v = get(key); return v instanceof JSONObject jo ? jo : null; }
        public JSONArray getJSONArray(String key) { Object v = get(key); return v instanceof JSONArray ja ? ja : null; }
        public <T> T getBean(String key, Class<T> clazz) {
            Object v = get(key);
            return v == null ? null : clazz.cast(bind(v, clazz, clazz));
        }
        public JSONObject putFluent(String key, Object value) { put(key, value); return this; }
    }

    /** A JSON array backed by an {@link ArrayList} with typed accessors (hutool-style). */
    public static class JSONArray extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;

        public String getStr(int index) { Object v = get(index); return v == null ? null : v.toString(); }
        public Integer getInt(int index) { Object v = get(index); return v == null ? null : ((Number) v).intValue(); }
        public Long getLong(int index) { Object v = get(index); return v == null ? null : ((Number) v).longValue(); }
        public JSONObject getJSONObject(int index) { Object v = get(index); return v instanceof JSONObject jo ? jo : null; }
        public JSONArray getJSONArray(int index) { Object v = get(index); return v instanceof JSONArray ja ? ja : null; }
        public <T> List<T> toList(Class<T> elementType) {
            List<T> result = new ArrayList<>(size());
            for (Object item : this) result.add(elementType.cast(bind(item, elementType, elementType)));
            return result;
        }
        public <T> T getBean(int index, Class<T> clazz) {
            Object v = get(index);
            return v == null ? null : clazz.cast(bind(v, clazz, clazz));
        }
    }
}