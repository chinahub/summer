package cn.jiebaba.summer.sample.office;

import cn.jiebaba.summer.office.pdf.PdfReader;
import cn.jiebaba.summer.office.pdf.PdfWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 测试（iText 使用方式）：模仿 iText 的经典套路 —— PdfWriter 生成文档，
 * 再用 PdfReader 打开并提取文本逐行校验；同时校验 PDF 骨架完整性与字体子结构
 * （无字体时回退 Helvetica，嵌入 TTF 时使用 Type0/CIDFontType2）。
 */
public class PdfItextStyleTest {


    /** iText 式回路：write 生成 PDF，read 提取文本，逐行比对内容。 */
    @Test
    public void writeThenExtractTextRoundTrip() throws Exception {
        byte[] pdf = new PdfWriter().write("Hello summer-office\nsecond line here\nthird line 123");
        String text = new PdfReader().read(new ByteArrayInputStream(pdf));
        Assertions.assertTrue(text.contains("Hello summer-office"), "应提取出第一行文本");
        Assertions.assertTrue(text.contains("second line here"), "应提取出第二行文本");
        Assertions.assertTrue(text.contains("third line 123"), "应提取出第三行文本");
    }

    /** 骨架校验：文件头、xref、startxref、trailer Root 与 EOF 标记齐全，阅读器可打开。 */
    @Test
    public void pdfSkeletonIsValid() throws Exception {
        byte[] pdf = new PdfWriter().write("skeleton check");
        String s = new String(pdf, StandardCharsets.ISO_8859_1);
        Assertions.assertTrue(s.startsWith("%PDF-1.4"), "应以 PDF 版本头开始");
        Assertions.assertTrue(s.trim().endsWith("%%EOF"), "应以 EOF 标记结束");
        Assertions.assertTrue(s.contains("xref"), "应包含交叉引用表");
        Assertions.assertTrue(s.contains("startxref"), "应包含 startxref 偏移");
        Assertions.assertTrue(s.contains("/Root 1 0 R"), "trailer 应指向文档目录");
    }

    /** 字体回退：不嵌入字体时使用基础 14 字体 Helvetica，且不含 CID 字体结构。 */
    @Test
    public void helveticaFallbackWithoutFont() throws Exception {
        byte[] pdf = new PdfWriter().write("plain ascii text");
        String s = new String(pdf, StandardCharsets.ISO_8859_1);
        Assertions.assertTrue(s.contains("/Helvetica"), "无字体时应回退 Helvetica");
        Assertions.assertFalse(s.contains("/Subtype /Type0"), "无字体时不应出现 Type0 字体");
    }

    /** 中文场景：嵌入系统 CJK 字体后应生成 Type0/CIDFontType2/FontFile2/ToUnicode 结构。 */
    @Test
    public void embeddedCjkFontStructure() throws Exception {
        Path font = findCjkFont();
        Assumptions.assumeTrue(font != null, "未找到系统 CJK 字体，跳过");
        byte[] pdf = new PdfWriter().withFont(font).write("你好，PDF 中文");
        String s = new String(pdf, StandardCharsets.ISO_8859_1);
        Assertions.assertTrue(s.contains("/Subtype /Type0"), "嵌入 TTF 应使用 Type0 字体");
        Assertions.assertTrue(s.contains("/CIDFontType2"), "应包含 CIDFontType2 子字体");
        Assertions.assertTrue(s.contains("/FontFile2"), "应嵌入 TrueType 字体文件流");
        Assertions.assertTrue(s.contains("/Filter /FlateDecode"), "字体文件流应压缩");
        Assertions.assertTrue(s.contains("/ToUnicode"), "应包含 ToUnicode 映射");
        Assertions.assertTrue(s.contains("/Identity-H"), "应使用 Identity-H 编码");
    }

    /** 依次尝试常见 Windows CJK 字体路径，返回首个存在者；全部缺失时返回 null。 */
    private static Path findCjkFont() {
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/msyh.ttc"
        };
        for (String path : candidates) {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }
}
