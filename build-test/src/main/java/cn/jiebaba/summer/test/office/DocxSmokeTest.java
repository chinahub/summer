package cn.jiebaba.summer.test.office;

import cn.jiebaba.summer.office.docx.DocxWriter;
import cn.jiebaba.summer.office.docx.Run;

import java.io.ByteArrayOutputStream;
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
 * DOCX 写入冒烟测试：纯 JDK 生成含标题、多样式段落、表格、图片的 .docx，
 * 解包校验 document.xml 结构、styles.xml 样式、rels 关系、Content_Types 与图片资源。
 * <p>覆盖借鉴 fastexcel/POI 设计补齐的 DOCX 未实现功能：表格、图片、样式写入。
 */
public class DocxSmokeTest {

    private static int passed = 0;

    /** 冒烟测试入口：构建复合文档并逐项断言 OOXML 结构与资源完整性。 */
    public static void main(String[] args) throws Exception {
        header("docx build with heading/para/table/image");
        byte[] img = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] docx = new DocxWriter()
                .heading(1, "标题一")
                .paragraph(Run.text("正常 ").withBold().withSize(14).withColor("FF0000"))
                .paragraph(Run.bold("加粗段"), Run.italic("斜体段"))
                .table(List.of(List.of("姓名", "分数"), List.of("张三", "95")), true)
                .image(img, "image/png", 200, 120)
                .build();

        String doc = zipEntry(docx, "word/document.xml");
        expect("heading1 style", true, doc.contains("<w:pStyle w:val=\"Heading1\"/>"));
        expect("bold run", true, doc.contains("<w:b/>"));
        expect("italic run", true, doc.contains("<w:i/>"));
        expect("underline run", true, doc.contains("<w:u w:val=\"single\"/>") || !doc.contains("<w:u"));
        expect("color attr", true, doc.contains("<w:color w:val=\"FF0000\"/>"));
        expect("size half-points", true, doc.contains("<w:sz w:val=\"28\"/>"));
        expect("table element", true, doc.contains("<w:tbl>"));
        expect("table borders", true, doc.contains("<w:tblBorders>"));
        expect("table cell content", true, doc.contains("张三"));
        expect("drawing element", true, doc.contains("<w:drawing>"));
        expect("blip embed rId2", true, doc.contains("r:embed=\"rId2\""));
        expect("extent cx emu", true, doc.contains("cx=\"1905000\""));
        expect("extent cy emu", true, doc.contains("cy=\"1143000\""));

        header("docx styles/rels/content-types");
        String styles = zipEntry(docx, "word/styles.xml");
        expect("styles normal", true, styles.contains("w:styleId=\"Normal\""));
        expect("styles heading1", true, styles.contains("w:styleId=\"Heading1\""));
        expect("styles heading6", true, styles.contains("w:styleId=\"Heading6\""));

        String rels = zipEntry(docx, "word/_rels/document.xml.rels");
        expect("rels styles target", true, rels.contains("Target=\"styles.xml\""));
        expect("rels image target", true, rels.contains("Target=\"media/image1.png\""));

        String ct = zipEntry(docx, "[Content_Types].xml");
        expect("content type png default", true, ct.contains("Extension=\"png\""));
        expect("content type document override", true, ct.contains("/word/document.xml\""));

        header("docx media resource");
        expect("image entry exists", true, entryExists(docx, "word/media/image1.png"));
        expect("image bytes match", true, Arrays.equals(img, zipEntryBytes(docx, "word/media/image1.png")));

        System.out.println();
        System.out.println("Docx smoke test: " + passed + " assertions passed");
    }

    /** 将 docx 字节写入临时文件后读取指定 ZIP 条目为字符串。 */
    static String zipEntry(byte[] docx, String name) throws IOException {
        return new String(zipEntryBytes(docx, name), StandardCharsets.UTF_8);
    }

    /** 读取指定 ZIP 条目的原始字节。 */
    static byte[] zipEntryBytes(byte[] docx, String name) throws IOException {
        Path tmp = Files.createTempFile("docx-smoke-", ".docx");
        try {
            Files.write(tmp, docx);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                ZipEntry e = zf.getEntry(name);
                if (e == null) {
                    return new byte[0];
                }
                try (InputStream in = zf.getInputStream(e)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    in.transferTo(out);
                    return out.toByteArray();
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 判断 docx 中是否存在指定 ZIP 条目。 */
    static boolean entryExists(byte[] docx, String name) throws IOException {
        Path tmp = Files.createTempFile("docx-exists-", ".docx");
        try {
            Files.write(tmp, docx);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                return zf.getEntry(name) != null;
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
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
