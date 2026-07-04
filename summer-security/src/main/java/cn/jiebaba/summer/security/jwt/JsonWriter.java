package cn.jiebaba.summer.security.jwt;

import java.util.Collection;
import java.util.Map;

/** 用于 JWT 声明 Map 的简易 JSON 序列化器（无第三方依赖）。 */
public final class JsonWriter {

    private JsonWriter() {}

    public static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        writeObject(sb, map);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (value instanceof Number n) {
            sb.append(n.toString());
        } else if (value instanceof Map<?, ?> m) {
            writeObject(sb, (Map<String, Object>) m);
        } else if (value instanceof Collection<?> c) {
            writeArray(sb, c);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<String, Object> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, e.getKey());
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Collection<?> items) {
        sb.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) sb.append(',');
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    /**
     * 将字符串转义后写入缓冲：处理引号、反斜杠及控制字符的 JSON 转义。
     */
    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
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
}
