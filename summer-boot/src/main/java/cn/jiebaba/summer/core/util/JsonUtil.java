package cn.jiebaba.summer.core.util;

import cn.jiebaba.summer.core.json.Json;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具，灵感来自 {@code cn.hutool.json.JSONUtil}。
 *
 * <p>本类为薄封装：所有序列化与解析均委托 {@link Json}（仅基于 JDK 实现），
 * 自身仅保留 hutool 风格的 {@link JSONObject}/{@link JSONArray} 便利类型与命名兼容，
 * 避免与 {@link Json} 维护两套独立实现。
 */
public final class JsonUtil {

    private JsonUtil() {}

    // ---- 序列化 -------------------------------------------------------

    /** 将任意值序列化为紧凑 JSON 字符串。 */
    public static String toJsonStr(Object value) {
        return Json.stringify(value);
    }

    /** 将任意值序列化为带缩进的 JSON 字符串。 */
    public static String toJsonPrettyStr(Object value) {
        return Json.toPretty(value);
    }

    /** 将字符串以 JSON 转义形式包裹双引号返回。 */
    public static String quote(String value) {
        return Json.quote(value);
    }

    /** 将字符串按 JSON 规则转义（不含外层引号）后返回；{@code null} 原样返回。 */
    public static String escape(String value) {
        return Json.escape(value);
    }

    /** 判断文本是否为合法 JSON（可解析即返回 true）。 */
    public static boolean isJson(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            Json.parse(text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- 解析 ---------------------------------------------------------

    /** 将 JSON 文本解析为通用结构（Map/List/基本类型）。 */
    public static Object parse(String json) {
        return Json.parse(json);
    }

    /**
     * 将 JSON 对象文本解析为 {@link JSONObject}，嵌套对象与数组递归转换为对应便利类型，
     * 以便通过 {@code getJSONObject}/{@code getJSONArray} 链式访问。
     */
    public static JSONObject parseObj(String json) {
        Object converted = convert(Json.parse(json));
        if (converted instanceof JSONObject jo) return jo;
        throw new IllegalArgumentException("JSON 不是对象: " + json);
    }

    /** 将 JSON 数组文本解析为 {@link JSONArray}，嵌套结构递归转换。 */
    public static JSONArray parseArray(String json) {
        Object converted = convert(Json.parse(json));
        if (converted instanceof JSONArray ja) return ja;
        throw new IllegalArgumentException("JSON 不是数组: " + json);
    }

    /** 将 JSON 文本绑定为目标类的实例。 */
    public static <T> T toBean(String json, Class<T> clazz) {
        return Json.parse(json, clazz);
    }

    /** 将 JSON 文本按泛型类型绑定，例如 {@code Map<String,Integer>}。 */
    public static <T> T toBean(String json, Type type) {
        return Json.parse(json, type);
    }

    /**
     * 将 JSON 数组文本绑定为指定元素类型的 List：先解析为通用数组，
     * 再逐个通过 {@link Json#bind} 绑定为目标元素类型。
     */
    public static <T> List<T> toList(String json, Class<T> elementType) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof List<?> list)) {
            throw new IllegalArgumentException("JSON 不是数组: " + json);
        }
        List<T> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(elementType.cast(Json.bind(item, elementType, elementType)));
        }
        return result;
    }

    /**
     * 递归将解析得到的通用结构转换为 hutool 风格的 {@link JSONObject}/{@link JSONArray}：
     * Map 转 JSONObject、List 转 JSONArray，其余原样返回。
     */
    private static Object convert(Object value) {
        if (value instanceof Map<?, ?> map) {
            JSONObject jo = new JSONObject();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                jo.put(String.valueOf(e.getKey()), convert(e.getValue()));
            }
            return jo;
        }
        if (value instanceof List<?> list) {
            JSONArray ja = new JSONArray();
            for (Object item : list) ja.add(convert(item));
            return ja;
        }
        return value;
    }

    /** 以 {@link LinkedHashMap} 支撑、带类型访问器的 JSON 对象（hutool 风格）。 */
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
            return v == null ? null : clazz.cast(Json.bind(v, clazz, clazz));
        }
        public JSONObject putFluent(String key, Object value) { put(key, value); return this; }
    }

    /** 以 {@link ArrayList} 支撑、带类型访问器的 JSON 数组（hutool 风格）。 */
    public static class JSONArray extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;

        public String getStr(int index) { Object v = get(index); return v == null ? null : v.toString(); }
        public Integer getInt(int index) { Object v = get(index); return v == null ? null : ((Number) v).intValue(); }
        public Long getLong(int index) { Object v = get(index); return v == null ? null : ((Number) v).longValue(); }
        public JSONObject getJSONObject(int index) { Object v = get(index); return v instanceof JSONObject jo ? jo : null; }
        public JSONArray getJSONArray(int index) { Object v = get(index); return v instanceof JSONArray ja ? ja : null; }
        public <T> List<T> toList(Class<T> elementType) {
            List<T> result = new ArrayList<>(size());
            for (Object item : this) result.add(elementType.cast(Json.bind(item, elementType, elementType)));
            return result;
        }
        public <T> T getBean(int index, Class<T> clazz) {
            Object v = get(index);
            return v == null ? null : clazz.cast(Json.bind(v, clazz, clazz));
        }
    }
}
