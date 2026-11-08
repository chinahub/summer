package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 日志敏感数据脱敏器：序列化前对 Map/List 树递归遍历，按大小写无关的敏感键
 * 将值替换为 {@value #MASK}，并对最终文本做长度上限截断。
 * 匹配规则：键含 {@code password}/{@code token}/{@code secret}，或为
 * {@code code}/{@code authorization}/{@code passwd}/{@code pwd}/{@code credential(s)}，
 * 因此 {@code postCode} 这类普通字段不会被误伤。
 */
public final class SensitiveDataSanitizer {

    public static final String MASK = "******";

    /** 日志文本（参数/返回摘要）的最大长度，超出截断防止撑爆日志列。 */
    public static final int MAX_LOG_TEXT = 2000;

    /** 精确匹配的敏感键（小写形式，含首尾空白容忍由调用方保证）。 */
    private static final Set<String> EXACT_KEYS = Set.of(
            "code", "authorization", "passwd", "pwd", "credential", "credentials");

    private SensitiveDataSanitizer() {
    }

    /**
     * 将任意对象序列化为可落库的日志文本：先转 JSON 树再递归脱敏，
     * 序列化失败时退化为 {@code String.valueOf} 并截断，绝不抛异常。
     *
     * @param value 待序列化的参数或返回值
     * @return 脱敏且截断后的日志文本
     */
    public static String toLogJson(Object value) {
        if (value == null) {
            return "";
        }
        String json;
        try {
            Object tree = Json.parse(Json.stringify(value));
            json = Json.stringify(mask(tree));
        } catch (RuntimeException serializeFailure) {
            json = String.valueOf(value);
        }
        return cap(json, MAX_LOG_TEXT);
    }

    /** 截断文本到指定上限（null 安全）。 */
    public static String cap(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 递归脱敏：Map 按键匹配替换，List 递归元素，标量原样返回。 */
    @SuppressWarnings("unchecked")
    private static Object mask(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                copy.put(key, sensitive(key) ? MASK : mask(entry.getValue()));
            }
            return copy;
        }
        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(mask(item));
            }
            return copy;
        }
        return node;
    }

    /** 判断键是否敏感：大小写无关。 */
    private static boolean sensitive(String key) {
        String lowered = key.toLowerCase(Locale.ROOT);
        return lowered.contains("password") || lowered.contains("token") || lowered.contains("secret")
                || EXACT_KEYS.contains(lowered);
    }
}
