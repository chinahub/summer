package cn.jiebaba.summer.core.env;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个足以解析 application.yml 配置文件的极简 YAML 解析器：支持基于缩进的嵌套 map、
 * 内联列表、块序列、标量与带引号字符串。并非完整的 YAML 实现（不支持
 * anchors/aliases/multi-doc），但覆盖了配置中常用的结构。返回嵌套的 Map/Object 树。
 */
public final class YamlParser {

    private YamlParser() {}

    public static Map<String, Object> parse(String text) {
        List<Line> lines = tokenize(text);
        Parser p = new Parser(lines, 0, lines.size(), 0);
        return p.parseMapping();
    }

    /** 将解析树展平为点分属性键（列表转为 [i] 索引）。 */
    public static Map<String, String> flatten(Map<String, Object> tree) {
        Map<String, String> out = new LinkedHashMap<>();
        flatten("", tree, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
                flatten(key, e.getValue(), out);
            }
        } else if (node instanceof List<?> list) {
            int i = 0;
            for (Object item : list) {
                flatten(prefix + "[" + i + "]", item, out);
                i++;
            }
        } else {
            out.put(prefix, node == null ? "" : String.valueOf(node));
        }
    }

    private static final class Line {
        final int indent;
        final String content;
        Line(int indent, String content) { this.indent = indent; this.content = content; }
    }

    private static List<Line> tokenize(String text) {
        List<Line> lines = new ArrayList<>();
        for (String raw : text.split("\r?\n", -1)) {
            String line = stripComment(raw);
            if (line.isBlank()) continue;
            if (line.trim().equals("---") || line.trim().equals("...")) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            lines.add(new Line(indent, line.substring(indent).trim()));
        }
        return lines;
    }

    private static String stripComment(String line) {
        boolean inSingle = false, inDouble = false;
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            if (c == '#' && !inSingle && !inDouble) {
                if (i == 0 || line.charAt(i - 1) == ' ' || line.charAt(i - 1) == '\t') break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static final class Parser {
        final List<Line> lines;
        int index;
        final int end;
        final int indent;

        Parser(List<Line> lines, int index, int end, int indent) {
            this.lines = lines; this.index = index; this.end = end; this.indent = indent;
        }

        Map<String, Object> parseMapping() {
            Map<String, Object> map = new LinkedHashMap<>();
            while (index < end) {
                Line line = lines.get(index);
                if (line.indent < indent) break;
                if (line.indent > indent) { index++; continue; }
                if (line.content.startsWith("- ")) {
                    // 映射层级的序列 —— 异常；防御性跳过
                    index++;
                    continue;
                }
                int colon = findColon(line.content);
                if (colon < 0) { index++; continue; }
                String key = line.content.substring(0, colon).trim();
                String rest = colon + 1 < line.content.length() ? line.content.substring(colon + 1).trim() : "";
                index++;
                if (rest.isEmpty()) {
                    Object child = parseChild();
                    Object existing = map.get(key);
                    if (existing instanceof List && child instanceof List) {
                        ((List<Object>) existing).addAll((List<?>) child);
                    } else {
                        map.put(key, child);
                    }
                } else {
                    map.put(key, parseScalarOrInline(rest));
                }
            }
            return map;
        }

        Object parseChild() {
            if (index >= end) return "";
            Line next = lines.get(index);
            if (next.indent <= indent) return "";
            if (next.content.startsWith("- ") || next.content.equals("-")) {
                return parseSequence(next.indent);
            }
            return new Parser(lines, index, end, next.indent).parseMappingAtIndent(next.indent);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parseMappingAtIndent(int ind) {
            Parser child = new Parser(lines, index, end, ind);
            Map<String, Object> result = child.parseMapping();
            this.index = child.index;
            return result;
        }

        List<Object> parseSequence(int seqIndent) {
            List<Object> list = new ArrayList<>();
            while (index < end) {
                Line line = lines.get(index);
                if (line.indent < seqIndent) break;
                if (line.indent > seqIndent) { index++; continue; }
                if (!line.content.startsWith("-")) break;
                String item = line.content.substring(1).trim();
                index++;
                if (item.isEmpty()) {
                    list.add(parseChild());
                } else if (item.contains(":") && !item.startsWith("'") && !item.startsWith("\"")
                        && !isFlowSequence(item) && !isFlowMapping(item)) {
                    // 内联映射项："- key: value"
                    List<Line> synthetic = new ArrayList<>();
                    int itemIndent = line.indent + 2;
                    synthetic.add(new Line(itemIndent, item));
                    int saved = index;
                    while (index < end && lines.get(index).indent > line.indent) {
                        synthetic.add(lines.get(index));
                        index++;
                    }
                    Parser ip = new Parser(synthetic, 0, synthetic.size(), itemIndent);
                    list.add(ip.parseMapping());
                } else {
                    list.add(parseScalarOrInline(item));
                }
            }
            return list;
        }

        Object parseScalarOrInline(String text) {
            if (text.startsWith("[") && text.endsWith("]")) {
                return parseFlowSequence(text.substring(1, text.length() - 1));
            }
            if (text.startsWith("{") && text.endsWith("}")) {
                return parseFlowMapping(text.substring(1, text.length() - 1));
            }
            return parseScalar(text);
        }

        List<Object> parseFlowSequence(String body) {
            List<Object> list = new ArrayList<>();
            for (String part : splitFlow(body)) {
                list.add(parseScalarOrInline(part.trim()));
            }
            return list;
        }

        Map<String, Object> parseFlowMapping(String body) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String part : splitFlow(body)) {
                int colon = findColon(part);
                if (colon >= 0) {
                    map.put(part.substring(0, colon).trim(), parseScalarOrInline(part.substring(colon + 1).trim()));
                }
            }
            return map;
        }

        static Object parseScalar(String text) {
            if (text == null) return null;
            String t = text.trim();
            if (t.isEmpty() || "null".equals(t) || "~".equals(t)) return null;
            if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
                return unquote(t);
            }
            if ("true".equalsIgnoreCase(t) || "yes".equalsIgnoreCase(t) || "on".equalsIgnoreCase(t)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(t) || "no".equalsIgnoreCase(t) || "off".equalsIgnoreCase(t)) return Boolean.FALSE;
            try { return Long.valueOf(t); } catch (NumberFormatException ignore) {}
            try { return Double.valueOf(t); } catch (NumberFormatException ignore) {}
            return t;
        }

        static String unquote(String t) {
            return t.substring(1, t.length() - 1).replace("\\\"", "\"").replace("\\'", "'");
        }
    }

    private static int findColon(String text) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            if (c == ':' && !inSingle && !inDouble) {
                if (i + 1 >= text.length() || text.charAt(i + 1) == ' ') return i;
            }
        }
        return -1;
    }

    private static boolean isFlowSequence(String s) { return s.startsWith("[") && s.endsWith("]"); }
    private static boolean isFlowMapping(String s) { return s.startsWith("{") && s.endsWith("}"); }

    /**
     * 将 YAML 流式结构（如 [a, b, c]）的单行内容拆分为子项列表。
     */
    private static List<String> splitFlow(String body) {
        List<String> parts = new ArrayList<>();
        if (body.isBlank()) return parts;
        int depth = 0;
        boolean inSingle = false, inDouble = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            if (!inSingle && !inDouble && (c == '[' || c == '{')) depth++;
            if (!inSingle && !inDouble && (c == ']' || c == '}')) depth--;
            if (c == ',' && depth == 0 && !inSingle && !inDouble) {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) parts.add(sb.toString());
        return parts;
    }
}
