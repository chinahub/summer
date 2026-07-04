package cn.jiebaba.summer.security.jwt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用于 JWT 负载的简易 JSON 解析器（无第三方依赖），返回 Map。 */
public final class JsonReader {

    private final String json;
    private int pos;

    private JsonReader(String json) {
        this.json = json;
        this.pos = 0;
    }

    public static Map<String, Object> read(String json) {
        JsonReader r = new JsonReader(json);
        r.skipWs();
        Object v = r.readValue();
        r.skipWs();
        if (r.pos < r.json.length()) throw new IllegalArgumentException("Trailing characters at " + r.pos);
        if (!(v instanceof Map)) throw new IllegalArgumentException("JWT payload is not a JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    private Object readValue() {
        skipWs();
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

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            Object value = readValue();
            map.put(key, value);
            skipWs();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; break; }
            throw new IllegalArgumentException("Expected , or } at " + pos);
        }
        return map;
    }

    private List<Object> readArray() {
        expect('[');
        java.util.List<Object> list = new java.util.ArrayList<>();
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(readValue());
            skipWs();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; break; }
            throw new IllegalArgumentException("Expected , or ] at " + pos);
        }
        return list;
    }

    /**
     * 读取并解析一个 JSON 字符串字面量，处理转义序列。
     */
    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= json.length()) throw new IllegalArgumentException("Unterminated escape");
                char e = json.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > json.length()) throw new IllegalArgumentException("Bad unicode escape");
                        sb.append((char) Integer.parseInt(json.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        boolean isFloat = false;
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c >= '0' && c <= '9') { pos++; continue; }
            if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isFloat = true; pos++; continue; }
            break;
        }
        String num = json.substring(start, pos);
        if (num.isEmpty()) throw new IllegalArgumentException("Invalid number at " + start);
        if (isFloat) return Double.parseDouble(num);
        try {
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            return Double.parseDouble(num);
        }
    }

    private Boolean readBoolean() {
        if (json.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (json.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new IllegalArgumentException("Invalid literal at " + pos);
    }

    private Object readNull() {
        if (json.startsWith("null", pos)) { pos += 4; return null; }
        throw new IllegalArgumentException("Invalid literal at " + pos);
    }

    private char peek() {
        if (pos >= json.length()) throw new IllegalArgumentException("Unexpected end of input");
        return json.charAt(pos);
    }

    private void expect(char c) {
        if (pos >= json.length() || json.charAt(pos) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
        }
        pos++;
    }

    private void skipWs() {
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
            else break;
        }
    }
}
