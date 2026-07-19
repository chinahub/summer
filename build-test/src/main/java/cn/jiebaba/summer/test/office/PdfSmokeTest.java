package cn.jiebaba.summer.test.office;

import cn.jiebaba.summer.office.pdf.PdfWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 写入冒烟测试：嵌入 TrueType 字体生成含中文的 PDF，校验 Type0/CIDFontType2/
 * FontFile2/ToUnicode 等字体子结构与 PDF 骨架完整性，并验证无字体时回退 Helvetica。
 * <p>覆盖补齐的未实现功能：PDF 中文（TTF 嵌入 + CID）。
 */
public class PdfSmokeTest {

    private static int passed = 0;

    /** 冒烟测试入口：优先使用系统 CJK 字体生成 PDF 并断言结构，再验证 Helvetica 回退。 */
    public static void main(String[] args) throws Exception {
        byte[] font = loadCjkFont();
        header("pdf cjk with embedded ttf");
        byte[] pdf = new PdfWriter().withFont(font).write("你好，PDF 中文！\n第二行测试\nHello 混排");
        String s = new String(pdf, StandardCharsets.ISO_8859_1);
        expect("pdf header", true, s.startsWith("%PDF-1.4"));
        expect("type0 font", true, s.contains("/Subtype /Type0"));
        expect("cidfonttype2", true, s.contains("/CIDFontType2"));
        expect("fontfile2", true, s.contains("/FontFile2"));
        expect("tounicode", true, s.contains("/ToUnicode"));
        expect("flate decode", true, s.contains("/Filter /FlateDecode"));
        expect("identity-h encoding", true, s.contains("/Identity-H"));
        expect("cidtogidmap identity", true, s.contains("/CIDToGIDMap /Identity"));
        expect("font descriptor", true, s.contains("/FontDescriptor"));
        expect("fontbbox", true, s.contains("/FontBBox"));
        expect("hex glyph tj", true, s.contains("> Tj"));
        expect("startxref", true, s.contains("startxref"));
        expect("xref table", true, s.contains("xref"));
        expect("trailer root", true, s.contains("/Root 1 0 R"));
        expect("eof marker", true, s.trim().endsWith("%%EOF"));

        header("pdf structure counts");
        expect("endstream count >= 3", true, count(s, "endstream") >= 3);
        expect("endobj count >= 9", true, count(s, "endobj") >= 9);

        header("pdf helvetica fallback");
        byte[] pdf2 = new PdfWriter().write("Hello World");
        String s2 = new String(pdf2, StandardCharsets.ISO_8859_1);
        expect("helvetica base font", true, s2.contains("/Helvetica"));
        expect("helvetica no type0", false, s2.contains("/Subtype /Type0"));
        expect("helvetica eof", true, s2.trim().endsWith("%%EOF"));

        System.out.println();
        System.out.println("Pdf smoke test: " + passed + " assertions passed");
    }

    /** 加载系统 CJK 字体：依次尝试 simhei.ttf、simsun.ttc、msyh.ttc，取首个存在者。 */
    private static byte[] loadCjkFont() throws Exception {
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/msyh.ttc"
        };
        for (String path : candidates) {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                System.out.println("  使用字体: " + path);
                return Files.readAllBytes(p);
            }
        }
        throw new IllegalStateException("未找到系统 CJK 字体（simhei.ttf/simsun.ttc/msyh.ttc）");
    }

    /** 统计子串出现次数。 */
    private static int count(String s, String t) {
        int c = 0;
        int i = 0;
        while ((i = s.indexOf(t, i)) >= 0) {
            c++;
            i += t.length();
        }
        return c;
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
