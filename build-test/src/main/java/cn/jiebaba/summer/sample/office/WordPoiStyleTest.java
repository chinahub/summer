package cn.jiebaba.summer.sample.office;

import cn.jiebaba.summer.office.docx.DocxReader;
import cn.jiebaba.summer.office.docx.DocxWriter;
import cn.jiebaba.summer.office.docx.Run;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Word 测试（POI 使用方式）：模仿 POI XWPF 的经典套路 —— 以“文档 -> 段落 -> Run”模型
 * 构建内容（标题、加粗/斜体/颜色/字号、表格、图片），写出后像 XWPFDocument 读回一样
 * 用 DocxReader 提取文本校验，并解包校验底层 OOXML（document.xml、styles.xml、media）。
 */
public class WordPoiStyleTest {


    /** POI 式回路：heading + 多 Run 段落写出，读回文本逐行比对（对应 XWPF 段落遍历）。 */
    @Test
    public void headingParagraphRunRoundTrip() throws Exception {
        byte[] docx = new DocxWriter()
                .heading(1, "季度报告")
                .paragraph(Run.text("本季度营收增长"), Run.bold("20%"), Run.text("，超出预期。"))
                .paragraph(Run.italic("数据仅供参考"))
                .build();

        String text = new DocxReader().read(new ByteArrayInputStream(docx));
        String[] lines = text.split("\n");
        Assertions.assertEquals("季度报告", lines[0]);
        Assertions.assertEquals("本季度营收增长20%，超出预期。", lines[1]);
        Assertions.assertEquals("数据仅供参考", lines[2]);
    }

    /** 表格场景：写出三行两列的表格，读回文本须包含全部单元格内容。 */
    @Test
    public void tableCellsReadBack() throws Exception {
        byte[] docx = new DocxWriter()
                .paragraph("成绩表")
                .table(List.of(List.of("姓名", "分数"),
                        List.of("张三", "95"),
                        List.of("李四", "88")), true)
                .build();

        String text = new DocxReader().read(new ByteArrayInputStream(docx));
        Assertions.assertTrue(text.contains("成绩表"), "应包含表前段落");
        Assertions.assertTrue(text.contains("姓名"), "应包含表头单元格");
        Assertions.assertTrue(text.contains("分数"), "应包含表头单元格");
        Assertions.assertTrue(text.contains("张三"), "应包含数据单元格");
        Assertions.assertTrue(text.contains("95"), "应包含数据单元格");
        Assertions.assertTrue(text.contains("李四"), "应包含数据单元格");
        Assertions.assertTrue(text.contains("88"), "应包含数据单元格");
    }

    /** Run 样式落盘：解包 document.xml，校验标题样式与加粗/斜体/颜色/字号的底层 OOXML 属性。 */
    @Test
    public void runStylesInDocumentXml() throws Exception {
        byte[] docx = new DocxWriter()
                .heading(2, "样式标题")
                .paragraph(Run.text("前缀"),
                        Run.bold("加粗"),
                        Run.italic("斜体"),
                        Run.text("红字").withColor("FF0000"),
                        Run.text("大字").withSize(14))
                .table(List.of(List.of("h1", "h2"), List.of("c1", "c2")), true)
                .build();

        String doc = zipEntry(docx, "word/document.xml");
        Assertions.assertTrue(doc.contains("<w:pStyle w:val=\"Heading2\"/>"), "标题应引用 Heading2 样式");
        Assertions.assertTrue(doc.contains("<w:b/>"), "加粗 Run 应输出 <w:b/>");
        Assertions.assertTrue(doc.contains("<w:i/>"), "斜体 Run 应输出 <w:i/>");
        Assertions.assertTrue(doc.contains("<w:color w:val=\"FF0000\"/>"), "颜色应输出 <w:color>");
        Assertions.assertTrue(doc.contains("<w:sz w:val=\"28\"/>"), "14 磅字号应为 28 半磅");
        Assertions.assertTrue(doc.contains("<w:tbl>"), "应包含表格元素");
        Assertions.assertTrue(doc.contains("<w:tblBorders>"), "表格应带边框定义");

        String styles = zipEntry(docx, "word/styles.xml");
        Assertions.assertTrue(styles.contains("w:styleId=\"Normal\""), "styles.xml 应定义 Normal 样式");
        Assertions.assertTrue(styles.contains("w:styleId=\"Heading2\""), "styles.xml 应定义 Heading2 样式");
    }

    /** 图片场景：嵌入图片后包内应存在 media 条目且字节一致，关系文件指向该资源。 */
    @Test
    public void imageEmbeddedInPackage() throws Exception {
        byte[] png = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] docx = new DocxWriter()
                .paragraph("下图：")
                .image(png, "image/png", 200, 120)
                .build();

        String doc = zipEntry(docx, "word/document.xml");
        Assertions.assertTrue(doc.contains("<w:drawing>"), "应包含 drawing 元素");
        String rels = zipEntry(docx, "word/_rels/document.xml.rels");
        Assertions.assertTrue(rels.contains("Target=\"media/image1.png\""), "关系文件应指向图片资源");
        Assertions.assertTrue(entryExists(docx, "word/media/image1.png"), "包内应存在图片条目");
        Assertions.assertTrue(Arrays.equals(png, zipEntryBytes(docx, "word/media/image1.png")),
                "图片字节应与写入一致");
    }

    /** 纯文本写出：OfficeWriter.write(content) 生成单段文档，读回文本一致。 */
    @Test
    public void writePlainTextContent() throws Exception {
        byte[] docx = new DocxWriter().write("纯文本段落内容");
        String text = new DocxReader().read(new ByteArrayInputStream(docx));
        Assertions.assertEquals("纯文本段落内容", text);
    }

    /** 将 docx 字节写入临时文件后读取指定 ZIP 条目为字符串（UTF-8）。 */
    static String zipEntry(byte[] docx, String name) throws IOException {
        return new String(zipEntryBytes(docx, name), StandardCharsets.UTF_8);
    }

    /** 读取 docx 中指定 ZIP 条目的原始字节；条目不存在时返回空数组。 */
    static byte[] zipEntryBytes(byte[] docx, String name) throws IOException {
        Path tmp = Files.createTempFile("word-poi-", ".docx");
        try {
            Files.write(tmp, docx);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                ZipEntry e = zf.getEntry(name);
                if (e == null) {
                    return new byte[0];
                }
                try (InputStream in = zf.getInputStream(e)) {
                    return in.readAllBytes();
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 判断 docx 包内是否存在指定 ZIP 条目。 */
    static boolean entryExists(byte[] docx, String name) throws IOException {
        Path tmp = Files.createTempFile("word-poi-exists-", ".docx");
        try {
            Files.write(tmp, docx);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                return zf.getEntry(name) != null;
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
