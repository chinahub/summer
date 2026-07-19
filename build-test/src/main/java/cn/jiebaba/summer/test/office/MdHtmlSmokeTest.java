package cn.jiebaba.summer.test.office;

import cn.jiebaba.summer.office.md.MdHtml;

/**
 * Markdown -> HTML 冒烟测试：覆盖标题、加粗/斜体/删除线、列表、代码、链接、图片、
 * 引用、水平线、有序列表与 HTML 转义。
 * <p>覆盖补齐的未实现功能：Markdown 转 HTML。
 */
public class MdHtmlSmokeTest {

    private static int passed = 0;

    /** 冒烟测试入口：转换复合 Markdown 文本并逐项断言生成的 HTML 片段。 */
    public static void main(String[] args) {
        header("markdown block + inline");
        String md = "# 标题\n\n**加粗** 与 *斜体* 以及 ~~删除~~\n\n- 列表a\n- 列表b\n\n`代码`\n\n[链接](http://x)\n\n> 引用文本\n\n1. 有序1\n2. 有序2";
        String html = MdHtml.toHtml(md);
        expect("h1 heading", true, html.contains("<h1>标题</h1>"));
        expect("strong bold", true, html.contains("<strong>加粗</strong>"));
        expect("em italic", true, html.contains("<em>斜体</em>"));
        expect("del strikethrough", true, html.contains("<del>删除</del>"));
        expect("ul list", true, html.contains("<ul>"));
        expect("ul item a", true, html.contains("<li>列表a</li>"));
        expect("ul item b", true, html.contains("<li>列表b</li>"));
        expect("code span", true, html.contains("<code>代码</code>"));
        expect("link anchor", true, html.contains("<a href=\"http://x\">链接</a>"));
        expect("blockquote", true, html.contains("<blockquote>引用文本</blockquote>"));
        expect("ol list", true, html.contains("<ol>"));
        expect("ol item 1", true, html.contains("<li>有序1</li>"));
        expect("ol item 2", true, html.contains("<li>有序2</li>"));

        header("markdown advanced");
        String adv = "## 二级\n\n```java\nint x = 1 < 2;\n```\n\n---\n\n![alt](/img.png)";
        String advHtml = MdHtml.toHtml(adv);
        expect("h2 heading", true, advHtml.contains("<h2>二级</h2>"));
        expect("fenced code pre", true, advHtml.contains("<pre><code"));
        expect("fenced code lang", true, advHtml.contains("class=\"language-java\""));
        expect("code escaped", true, advHtml.contains("1 &lt; 2"));
        expect("hr", true, advHtml.contains("<hr/>"));
        expect("image", true, advHtml.contains("<img src=\"/img.png\" alt=\"alt\"/>"));

        header("html escaping");
        String esc = MdHtml.toHtml("<script>alert('&')");
        expect("escape lt", true, esc.contains("&lt;script&gt;"));
        expect("escape amp", true, esc.contains("&amp;"));

        header("empty input");
        expect("empty returns empty", "", MdHtml.toHtml(""));
        expect("null returns empty", "", MdHtml.toHtml(null));

        System.out.println();
        System.out.println("MdHtml smoke test: " + passed + " assertions passed");
    }

    static void header(String name) {
        System.out.println("== " + name + " ==");
    }

    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) {
            passed++;
        } else {
            System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
