package cn.jiebaba.summer.core.json;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 {@code byte[]} 的流式 JSON 读取器（UTF-8 直接解析）。
 * <p>不构建中间通用树，按需逐 token 读取，供 {@link Json} 的 streaming-bind 反序列化使用。
 * 结构字符（{@code " { } [ ] : ,} 及数字/字面量首字节）均为 ASCII（&lt; 0x80），
 * 而 UTF-8 续字节均 ≥ 0x80，故字节级扫描结构不会误判多字节字符。
 */
final class JsonReader {
    private final byte[] src;
    private final int len;
    private int pos;

    JsonReader(byte[] src) {
        this.src = src;
        this.len = src.length;
    }

    /** 跳过空白（空格/制表/换行/回车）。 */
    void skipWs() {
        while (pos < len) {
            byte c = src[pos];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    /** 返回下一字节（0-255），EOF 返回 -1；不前进。 */
    int peek() {
        return pos < len ? (src[pos] & 0xFF) : -1;
    }

    /** 进入对象：跳空白后消费 {@code {}。 */
    void beginObject() {
        skipWs();
        expect('{');
    }

    /** 离开对象：跳空白后消费 {@code }}。 */
    void endObject() {
        skipWs();
        expect('}');
    }

    /** 进入数组：跳空白后消费 {@code [}。 */
    void beginArray() {
        skipWs();
        expect('[');
    }

    /** 离开数组：跳空白后消费 {@code ]}。 */
    void endArray() {
        skipWs();
        expect(']');
    }

    /**
     * 在对象/数组内判断是否还有下一个元素：遇 {@code ,} 消费并返回 true；
     * 遇结束符（{@code } ]}）或 EOF 返回 false。
     *
     * @return 是否还有后续元素
     */
    boolean hasNext() {
        skipWs();
        int c = peek();
        if (c == ',') {
            pos++;
            return true;
        }
        return c != '}' && c != ']' && c != -1;
    }

    /** 读取对象字段名：读 JSON 字符串后消费随后的 {@code :}。 */
    String nextName() {
        skipWs();
        String name = readString();
        skipWs();
        expect(':');
        return name;
    }

    /** 读取字符串值（跳空白后位于起始引号）。 */
    String nextString() {
        skipWs();
        return readString();
    }

    /** 读取布尔值。 */
    boolean nextBoolean() {
        skipWs();
        return readBoolean();
    }

    /** 消费 null 字面量。 */
    void nextNull() {
        skipWs();
        readNull();
    }

    /** 读取数字值，整数返回 Long、浮点返回 Double。 */
    Number nextNumber() {
        skipWs();
        return readNumber();
    }

    /** 读取字面量值并返回自然类型（Map/List/String/Long/Double/Boolean/null），仅用于 Object 目标。 */
    Object nextLiteral() {
        skipWs();
        return readLiteral();
    }

    /** 跳过任意一个值（用于未知字段）。 */
    void skipValue() {
        skipWs();
        int c = peek();
        switch (c) {
            case '"' -> readString();
            case '{' -> {
                beginObject();
                while (hasNext()) {
                    nextName();
                    skipValue();
                }
                endObject();
            }
            case '[' -> {
                beginArray();
                while (hasNext()) {
                    skipValue();
                }
                endArray();
            }
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        }
    }

    /** 校验读取完成后无尾随内容，有则抛异常。 */
    void checkTrailing() {
        skipWs();
        if (pos < len) {
            throw new IllegalArgumentException("Unexpected trailing content at position " + pos);
        }
    }

    private void expect(int c) {
        if (pos >= len || (src[pos] & 0xFF) != c) {
            throw new IllegalArgumentException("Expected '" + (char) c + "' at position " + pos);
        }
        pos++;
    }

    /**
     * 读取 JSON 字符串（已位于起始引号处）。无转义时直接对字节切片做 UTF-8 解码，避免拷贝；
     * 遇反斜杠转交慢路径逐段解码。
     *
     * @return 解码后的字符串
     */
    private String readString() {
        expect('"');
        int start = pos;
        for (int i = pos; i < len; i++) {
            byte c = src[i];
            if (c == '\\') {
                return readStringSlow(start);
            }
            if (c == '"') {
                String s = new String(src, start, i - start, StandardCharsets.UTF_8);
                pos = i + 1;
                return s;
            }
        }
        throw new IllegalArgumentException("Unterminated string at position " + start);
    }

    /**
     * 含转义的字符串慢路径：从 {@code start} 重新扫描，逐段收集 UTF-8 原始字节并以 UTF-8 解码，
     * 遇反斜杠按 JSON 转义规则处理。
     *
     * @param start 字符串内容起始位置（起始引号之后）
     * @return 解码后的字符串
     */
    private String readStringSlow(int start) {
        pos = start;
        StringBuilder sb = new StringBuilder();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        while (pos < len) {
            byte c = src[pos];
            if (c == '"') {
                flushRaw(sb, raw);
                pos++;
                return sb.toString();
            }
            if (c == '\\') {
                flushRaw(sb, raw);
                pos++;
                if (pos >= len) {
                    throw new IllegalArgumentException("Unterminated escape");
                }
                byte esc = src[pos++];
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
                        if (pos + 4 > len) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = new String(src, pos, 4, StandardCharsets.UTF_8);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape \\" + (char) esc);
                }
            } else {
                raw.write(c);
                pos++;
            }
        }
        throw new IllegalArgumentException("Unterminated string at position " + start);
    }

    /** 将累积的原始字节以 UTF-8 解码追加到 {@code sb}，并清空缓冲。 */
    private void flushRaw(StringBuilder sb, ByteArrayOutputStream raw) {
        if (raw.size() > 0) {
            sb.append(new String(raw.toByteArray(), 0, raw.size(), StandardCharsets.UTF_8));
            raw.reset();
        }
    }

    /**
     * 读取数字（已位于数字起始处）：整数直接解析为 Long（位数 ≤ 18 免 String 分配），
     * 浮点或超长整数回退 {@code String→Double/Long} 解析。
     *
     * @return Long 或 Double
     */
    private Number readNumber() {
        int start = pos;
        if (pos < len && src[pos] == '-') {
            pos++;
        }
        boolean floating = false;
        while (pos < len) {
            byte c = src[pos];
            if (c >= '0' && c <= '9') {
                pos++;
                continue;
            }
            if (c == '.') {
                floating = true;
                pos++;
                continue;
            }
            if (c == 'e' || c == 'E') {
                floating = true;
                pos++;
                if (pos < len && (src[pos] == '+' || src[pos] == '-')) {
                    pos++;
                }
                continue;
            }
            break;
        }
        if (pos == start || (pos == start + 1 && src[start] == '-')) {
            throw new IllegalArgumentException("Invalid number at position " + start);
        }
        if (!floating) {
            int digits = pos - start - (src[start] == '-' ? 1 : 0);
            if (digits <= 18) {
                return parseLong(start, pos);
            }
        }
        String token = new String(src, start, pos - start, StandardCharsets.UTF_8);
        return floating ? Double.parseDouble(token) : Long.parseLong(token);
    }

    /**
     * 手工解析整数区间为 long，避免 String 分配；调用方保证位数 ≤ 18 不会溢出 long。
     *
     * @param start 区间起点（含）
     * @param end   区间终点（不含）
     * @return 解析得到的 long
     */
    private long parseLong(int start, int end) {
        boolean neg = false;
        int i = start;
        if (src[i] == '-') {
            neg = true;
            i++;
        }
        long v = 0;
        while (i < end) {
            v = v * 10 + (src[i] - '0');
            i++;
        }
        return neg ? -v : v;
    }

    private boolean readBoolean() {
        if (match("true")) {
            return true;
        }
        if (match("false")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid literal at position " + pos);
    }

    private void readNull() {
        if (!match("null")) {
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }
    }

    private boolean match(String word) {
        if (pos + word.length() > len) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (src[pos + i] != word.charAt(i)) {
                return false;
            }
        }
        pos += word.length();
        return true;
    }

    /** 读取字面量值（Object 目标用）：对象→LinkedHashMap，数组→ArrayList，其余标量。 */
    private Object readLiteral() {
        int c = peek();
        return switch (c) {
            case '{' -> readLiteralObject();
            case '[' -> readLiteralArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> {
                readNull();
                yield null;
            }
            default -> readNumber();
        };
    }

    private Map<String, Object> readLiteralObject() {
        beginObject();
        Map<String, Object> map = new LinkedHashMap<>();
        while (hasNext()) {
            String key = nextName();
            map.put(key, readLiteral());
        }
        endObject();
        return map;
    }

    private List<Object> readLiteralArray() {
        beginArray();
        List<Object> list = new ArrayList<>();
        while (hasNext()) {
            list.add(readLiteral());
        }
        endArray();
        return list;
    }
}
