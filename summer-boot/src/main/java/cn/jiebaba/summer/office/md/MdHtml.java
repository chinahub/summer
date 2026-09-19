package cn.jiebaba.summer.office.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown -> HTML 转换器：纯 JDK 实现（CommonMark 子集 + GFM 删除线），零第三方依赖。
 * <p>支持块级：ATX 标题（#..######）、围栏代码块（``` / ~~~，含语言类）、水平线、
 * 引用块、无序列表（- * +）、有序列表（1.）、段落；行内：行内代码、图片、链接、
 * 加粗、斜体、删除线、硬换行。HTML 特殊字符自动转义，代码块/行内代码内容不解析 Markdown。
 *
 * <pre>{@code
 * String html = MdHtml.toHtml("# 标题\n正文 **加粗** 与 `代码`");
 * }</pre>
 */
public final class MdHtml {

    private MdHtml() {
    }

    /** 将 Markdown 文本转换为 HTML 片段（多个块拼接，不含 &lt;html&gt; 包裹）。 */
    public static String toHtml(String md) {
        if (md == null || md.isEmpty()) {
            return "";
        }
        String normalized = md.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder(md.length() * 2);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.isBlank()) {
                i++;
                continue;
            }
            // 围栏代码块
            String fenceLang = fenceLanguage(line);
            if (fenceLang != null) {
                String fence = line.trim().substring(0, 1);
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().equals(fence.repeat(3)) && !lines[i].trim().startsWith(fence + fence + fence)) {
                    code.append(lines[i]).append('\n');
                    i++;
                }
                if (i < lines.length) {
                    i++; // 跳过结束围栏
                }
                out.append("<pre><code");
                if (!fenceLang.isEmpty()) {
                    out.append(" class=\"language-").append(escapeAttr(fenceLang)).append("\"");
                }
                out.append(">").append(escapeHtml(code.toString())).append("</code></pre>\n");
                continue;
            }
            // 标题
            Matcher hm = HEADING.matcher(line);
            if (hm.matches()) {
                out.append("<h").append(hm.group(1).length()).append(">")
                        .append(inline(hm.group(2).trim()))
                        .append("</h").append(hm.group(1).length()).append(">\n");
                i++;
                continue;
            }
            // 水平线
            if (HR.matcher(line).matches()) {
                out.append("<hr/>\n");
                i++;
                continue;
            }
            // 引用块
            if (line.trim().startsWith(">")) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    String content = lines[i].trim().substring(1);
                    if (!content.isEmpty() && content.charAt(0) == ' ') {
                        content = content.substring(1);
                    }
                    quote.append(content).append('\n');
                    i++;
                }
                out.append("<blockquote>").append(inline(quote.toString().trim())).append("</blockquote>\n");
                continue;
            }
            // 列表
            Matcher ul = UL_ITEM.matcher(line);
            Matcher ol = OL_ITEM.matcher(line);
            if (ul.matches() || ol.matches()) {
                boolean ordered = ol.matches();
                out.append(ordered ? "<ol>\n" : "<ul>\n");
                while (i < lines.length) {
                    String cur = lines[i];
                    if (cur.isBlank()) {
                        // 允许列表项间一个空行，若其后仍为同型列表项则继续
                        if (i + 1 < lines.length && (UL_ITEM.matcher(lines[i + 1]).matches() || OL_ITEM.matcher(lines[i + 1]).matches())) {
                            i++;
                            continue;
                        }
                        break;
                    }
                    Matcher m = ordered ? OL_ITEM.matcher(cur) : UL_ITEM.matcher(cur);
                    if (!m.matches()) {
                        break;
                    }
                    out.append("<li>").append(inline(m.group(1).trim())).append("</li>\n");
                    i++;
                }
                out.append(ordered ? "</ol>\n" : "</ul>\n");
                continue;
            }
            // 段落：连续非空非块级行
            StringBuilder para = new StringBuilder(line);
            i++;
            while (i < lines.length && !lines[i].isBlank()
                    && fenceLanguage(lines[i]) == null
                    && !HEADING.matcher(lines[i]).matches()
                    && !HR.matcher(lines[i]).matches()
                    && !lines[i].trim().startsWith(">")
                    && !UL_ITEM.matcher(lines[i]).matches()
                    && !OL_ITEM.matcher(lines[i]).matches()) {
                para.append('\n').append(lines[i]);
                i++;
            }
            out.append("<p>").append(inline(para.toString())).append("</p>\n");
        }
        return out.toString().trim();
    }

    /** 判断是否为围栏代码块起始行，返回语言名（可为空串）；非围栏返回 null。 */
    private static String fenceLanguage(String line) {
        String t = line.trim();
        if (t.startsWith("```")) {
            return t.substring(3).trim();
        }
        if (t.startsWith("~~~")) {
            return t.substring(3).trim();
        }
        return null;
    }

    // ==================== 行内处理 ====================

    /** 行内转换：转义 HTML、抽取行内代码、图片、链接、加粗、斜体、删除线、硬换行。 */
    private static String inline(String s) {
        // 1. 转义 HTML 特殊字符（保留 Markdown 语法字符）
        String t = escapeHtml(s);
        // 2. 抽取行内代码（内容不再做 Markdown 处理）
        List<String> codes = new ArrayList<>();
        Matcher cm = CODE_SPAN.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (cm.find()) {
            codes.add(cm.group(1));
            cm.appendReplacement(sb, "\u0000" + (codes.size() - 1) + "\u0000");
        }
        cm.appendTail(sb);
        t = sb.toString();
        // 3. 图片（先于链接，避免 ! 被 [text](url) 误匹配）
        t = IMG.matcher(t).replaceAll(mr -> "<img src=\"" + escapeAttr(mr.group(2)) + "\" alt=\"" + escapeAttr(mr.group(1)) + "\"/>");
        // 4. 链接
        t = LINK.matcher(t).replaceAll(mr -> "<a href=\"" + escapeAttr(mr.group(2)) + "\">" + mr.group(1) + "</a>");
        // 5. 加粗 / 斜体 / 删除线
        t = BOLD_A.matcher(t).replaceAll("<strong>$1</strong>");
        t = BOLD_U.matcher(t).replaceAll("<strong>$1</strong>");
        t = ITALIC_A.matcher(t).replaceAll("<em>$1</em>");
        t = ITALIC_U.matcher(t).replaceAll("<em>$1</em>");
        t = STRIKE.matcher(t).replaceAll("<del>$1</del>");
        // 6. 硬换行（两空格结尾或反斜杠结尾）
        t = t.replaceAll(" {2}\n", "<br/>\n").replace("\\\n", "<br/>\n");
        // 7. 还原行内代码
        for (int k = 0; k < codes.size(); k++) {
            t = t.replace("\u0000" + k + "\u0000", "<code>" + codes.get(k) + "</code>");
        }
        return t;
    }

    /** 转义 HTML 文本中的 &、<、>。 */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 转义 HTML 属性值中的 &、<、>、"。 */
    private static String escapeAttr(String s) {
        return escapeHtml(s).replace("\"", "&quot;");
    }

    // ==================== 预编译正则 ====================

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)(?:\\s*#+\\s*)?$");
    private static final Pattern HR = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern UL_ITEM = Pattern.compile("^ {0,3}[-*+]\\s+(.*)$");
    private static final Pattern OL_ITEM = Pattern.compile("^ {0,3}\\d+\\.\\s+(.*)$");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern IMG = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern BOLD_A = Pattern.compile("\\*\\*([^*]+?)\\*\\*");
    private static final Pattern BOLD_U = Pattern.compile("__([^_]+?)__");
    private static final Pattern ITALIC_A = Pattern.compile("\\*([^*]+?)\\*");
    private static final Pattern ITALIC_U = Pattern.compile("_([^_]+?)_");
    private static final Pattern STRIKE = Pattern.compile("~~([^~]+?)~~");
}
