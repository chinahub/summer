package cn.jiebaba.summer.office.docx;

import cn.jiebaba.summer.office.OfficeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * DOCX 写入器：纯 JDK 实现（java.util.zip），将文本内容生成为 .docx 字节数组，零第三方依赖。
 * <p>DOCX 本质为 ZIP + OOXML XML；本类直接拼装最小合法 DOCX 结构：
 * {@code [Content_Types].xml}、{@code _rels/.rels}、{@code word/document.xml}，
 * 按换行符拆分文本，每行生成一个段落（{@code <w:p><w:r><w:t>...</w:t></w:r></w:p>}）。
 *
 * <pre>{@code
 * byte[] docx = new DocxWriter().write("Hello\nWorld");
 * }</pre>
 */
public class DocxWriter implements OfficeWriter {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String NS_W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    @Override
    public byte[] write(String content) throws IOException {
        String text = content == null ? "" : content;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            writeContentTypes(zip);
            writeRootRels(zip);
            writeDocument(zip, text);
        }
        return out.toByteArray();
    }

    /** 写出 [Content_Types].xml：声明 rels 与 document 的内容类型。 */
    private static void writeContentTypes(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        writeRaw(zip, XML_HEADER);
        writeRaw(zip, "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        writeRaw(zip, "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        writeRaw(zip, "<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        writeRaw(zip, "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
        writeRaw(zip, "</Types>");
        zip.closeEntry();
    }

    /** 写出 _rels/.rels：根关系指向 word/document.xml。 */
    private static void writeRootRels(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("_rels/.rels"));
        writeRaw(zip, XML_HEADER);
        writeRaw(zip, "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        writeRaw(zip, "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>");
        writeRaw(zip, "</Relationships>");
        zip.closeEntry();
    }

    /** 写出 word/document.xml：按行拆分文本，每行一个段落。 */
    private static void writeDocument(ZipOutputStream zip, String text) throws IOException {
        zip.putNextEntry(new ZipEntry("word/document.xml"));
        StringBuilder sb = new StringBuilder(256 + text.length() * 2);
        sb.append(XML_HEADER);
        sb.append("<w:document ").append(NS_W).append("><w:body>");
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            sb.append("<w:p><w:r><w:t xml:space=\"preserve\">");
            sb.append(escapeXml(line));
            sb.append("</w:t></w:r></w:p>");
        }
        sb.append("</w:body></w:document>");
        writeRaw(zip, sb.toString());
        zip.closeEntry();
    }

    /** 转义 XML 特殊字符。 */
    private static String escapeXml(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void writeRaw(ZipOutputStream zip, String text) throws IOException {
        zip.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
