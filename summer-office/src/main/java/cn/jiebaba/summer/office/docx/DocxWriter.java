package cn.jiebaba.summer.office.docx;

import cn.jiebaba.summer.office.OfficeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * DOCX 写入器：纯 JDK 实现（java.util.zip），生成分段、标题、表格、图片的 .docx，零第三方依赖。
 * <p>DOCX 本质为 ZIP + OOXML XML；本类以构建器模式累积正文元素，{@link #build()} 时拼装完整结构：
 * {@code [Content_Types].xml}、{@code _rels/.rels}、{@code word/document.xml}、
 * {@code word/_rels/document.xml.rels}、{@code word/styles.xml}，以及可选的 {@code word/media/imageN.ext}。
 * <p>支持：段落（{@link Run} 多段样式）、标题（Heading1-6 样式）、表格（带边框、可选表头加粗）、
 * 图片（PNG/JPEG/GIF/BMP 内嵌，按像素换算 EMU）。
 *
 * <pre>{@code
 * byte[] docx = new DocxWriter()
 *         .heading(1, "标题")
 *         .paragraph(Run.text("正常 ").bold().text("加粗"))
 *         .table(List.of(List.of("姓名","分数"), List.of("张三","95")), true)
 *         .image(pngBytes, "image/png", 200, 120)
 *         .build();
 * }</pre>
 */
public class DocxWriter implements OfficeWriter {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String NS_W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";
    private static final String NS_R = "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"";
    private static final String NS_DRAW =
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"";
    /** 像素到 EMU 换算系数（96 DPI 下 1px = 9525 EMU）。 */
    private static final int EMU_PER_PX = 9525;

    /** 正文元素 XML 片段列表（段落/标题/表格/图片），按加入顺序写出。 */
    private final List<String> body = new ArrayList<>();
    /** 图片资源列表：rId -> {扩展名, 内容类型, 字节数据}，按加入顺序分配 rId2 起。 */
    private final List<Image> images = new ArrayList<>();

    public DocxWriter() {
    }

    /** 追加纯文本段落。 */
    public DocxWriter paragraph(String text) {
        return paragraph(new Run(text == null ? "" : text));
    }

    /** 追加由多个运行组成的段落（支持混合样式）。 */
    public DocxWriter paragraph(Run... runs) {
        body.add(paragraphXml(null, runs));
        return this;
    }

    /** 追加指定级别的标题（1-6，超出范围自动夹断）；使用 Heading 样式。 */
    public DocxWriter heading(int level, String text) {
        return heading(level, new Run(text == null ? "" : text));
    }

    /** 追加指定级别的标题，由多个运行组成。 */
    public DocxWriter heading(int level, Run... runs) {
        int lvl = Math.max(1, Math.min(6, level));
        body.add(paragraphXml("Heading" + lvl, runs));
        return this;
    }

    /** 追加表格（无表头加粗）。 */
    public DocxWriter table(List<List<String>> rows) {
        return table(rows, false);
    }

    /** 追加表格；firstRowHeader 为 true 时首行单元格加粗。 */
    public DocxWriter table(List<List<String>> rows, boolean firstRowHeader) {
        body.add(tableXml(rows, firstRowHeader));
        return this;
    }

    /** 内嵌图片；mime 决定扩展名与内容类型，widthPx/heightPx 为显示尺寸（96 DPI 换算 EMU）。 */
    public DocxWriter image(byte[] data, String mime, int widthPx, int heightPx) {
        if (data == null || data.length == 0) {
            return this;
        }
        int id = images.size() + 1;
        String rId = "rId" + (id + 1);
        Image img = new Image(id, rId, mime, data, widthPx * EMU_PER_PX, heightPx * EMU_PER_PX);
        images.add(img);
        body.add(imageParagraphXml(img));
        return this;
    }

    /** 构建并返回 .docx 字节数组；调用后构建器状态不变，可重复构建。 */
    public byte[] build() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            writeContentTypes(zip);
            writeRootRels(zip);
            writeDocument(zip);
            writeDocumentRels(zip);
            writeStyles(zip);
            for (int i = 0; i < images.size(); i++) {
                writeImage(zip, images.get(i), i + 1);
            }
        }
        return out.toByteArray();
    }

    /** {@link OfficeWriter} 契约：按换行拆分纯文本为段落并构建。 */
    @Override
    public byte[] write(String content) throws IOException {
        DocxWriter w = new DocxWriter();
        String text = content == null ? "" : content;
        for (String line : text.split("\n", -1)) {
            w.paragraph(line);
        }
        return w.build();
    }

    // ==================== XML 片段构造 ====================

    /** 构造段落 XML：pStyle 非 null 时加段落样式（标题），每个 Run 生成带 rPr 的 <w:r>。 */
    private static String paragraphXml(String pStyle, Run... runs) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("<w:p>");
        if (pStyle != null) {
            sb.append("<w:pPr><w:pStyle w:val=\"").append(pStyle).append("\"/></w:pPr>");
        }
        for (Run run : runs) {
            sb.append(runXml(run));
        }
        sb.append("</w:p>");
        return sb.toString();
    }

    /** 构造运行 XML：有样式时生成 <w:rPr>，文本经 XML 转义并保留首尾空格。 */
    private static String runXml(Run run) {
        StringBuilder sb = new StringBuilder(48 + run.text().length());
        sb.append("<w:r>");
        if (run.bold() || run.italic() || run.underline() || run.size() > 0 || run.color() != null) {
            sb.append("<w:rPr>");
            if (run.bold()) {
                sb.append("<w:b/>");
            }
            if (run.italic()) {
                sb.append("<w:i/>");
            }
            if (run.underline()) {
                sb.append("<w:u w:val=\"single\"/>");
            }
            if (run.size() > 0) {
                sb.append("<w:sz w:val=\"").append(run.size()).append("\"/>");
            }
            if (run.color() != null) {
                sb.append("<w:color w:val=\"").append(run.color()).append("\"/>");
            }
            sb.append("</w:rPr>");
        }
        sb.append("<w:t xml:space=\"preserve\">").append(escapeXml(run.text())).append("</w:t>");
        sb.append("</w:r>");
        return sb.toString();
    }

    /** 构造表格 XML：全宽单边框表格，firstRowHeader 时首行单元格加粗。 */
    private static String tableXml(List<List<String>> rows, boolean firstRowHeader) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("<w:tbl>");
        sb.append("<w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/>");
        sb.append("<w:tblBorders>");
        for (String edge : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) {
            sb.append("<w:").append(edge).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>");
        }
        sb.append("</w:tblBorders></w:tblPr>");
        for (int r = 0; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            boolean header = firstRowHeader && r == 0;
            sb.append("<w:tr>");
            for (String cell : cells) {
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>");
                sb.append("<w:p>").append(runXml(header ? Run.bold(cell == null ? "" : cell) : new Run(cell == null ? "" : cell))).append("</w:p>");
                sb.append("</w:tc>");
            }
            sb.append("</w:tr>");
        }
        sb.append("</w:tbl>");
        // 表格后需一个空段落，否则部分阅读器报错
        sb.append("<w:p/>");
        return sb.toString();
    }

    /** 构造图片段落 XML：单运行内嵌 drawing，引用图片 rId 与 EMU 尺寸。 */
    private static String imageParagraphXml(Image img) {
        long cx = img.widthEmu;
        long cy = img.heightEmu;
        String rId = img.rId;
        return "<w:p><w:r><w:drawing>"
                + "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                + "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
                + "<wp:docPr id=\"" + img.id + "\" name=\"Picture " + img.id + "\"/>"
                + "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>"
                + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:nvPicPr><pic:cNvPr id=\"" + img.id + "\" name=\"image" + img.id + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
                + "<pic:blipFill><a:blip r:embed=\"" + rId + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>"
                + "</pic:pic></a:graphicData></a:graphic>"
                + "</wp:inline></w:drawing></w:r></w:p>";
    }

    // ==================== ZIP 部分写出 ====================

    /** 写出 [Content_Types].xml：声明 rels/xml 默认类型与图片扩展名默认类型、document/styles 覆盖类型。 */
    private void writeContentTypes(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        StringBuilder sb = new StringBuilder(256);
        sb.append(XML_HEADER);
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        Map<String, String> extCt = new LinkedHashMap<>();
        for (Image img : images) {
            extCt.putIfAbsent(img.ext, img.contentType);
        }
        for (Map.Entry<String, String> e : extCt.entrySet()) {
            sb.append("<Default Extension=\"").append(e.getKey()).append("\" ContentType=\"").append(e.getValue()).append("\"/>");
        }
        sb.append("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
        sb.append("<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>");
        sb.append("</Types>");
        writeRaw(zip, sb.toString());
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

    /** 写出 word/document.xml：body 内依次拼接各元素 XML，末尾加 sectPr。 */
    private void writeDocument(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("word/document.xml"));
        StringBuilder sb = new StringBuilder(256 + body.stream().mapToInt(String::length).sum());
        sb.append(XML_HEADER);
        sb.append("<w:document ").append(NS_W).append(' ').append(NS_R).append(' ').append(NS_DRAW).append("><w:body>");
        for (String element : body) {
            sb.append(element);
        }
        sb.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>");
        sb.append("</w:body></w:document>");
        writeRaw(zip, sb.toString());
        zip.closeEntry();
    }

    /** 写出 word/_rels/document.xml.rels：rId1 指向 styles.xml，rId2+ 指向各图片。 */
    private void writeDocumentRels(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
        StringBuilder sb = new StringBuilder(128);
        sb.append(XML_HEADER);
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        sb.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        for (int i = 0; i < images.size(); i++) {
            Image img = images.get(i);
            sb.append("<Relationship Id=\"").append(img.rId).append("\"")
                    .append(" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\"")
                    .append(" Target=\"media/image").append(i + 1).append(".").append(img.ext).append("\"/>");
        }
        sb.append("</Relationships>");
        writeRaw(zip, sb.toString());
        zip.closeEntry();
    }

    /** 写出 word/styles.xml：Normal 与 Heading1-6 样式定义。 */
    private static void writeStyles(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("word/styles.xml"));
        StringBuilder sb = new StringBuilder(512);
        sb.append(XML_HEADER);
        sb.append("<w:styles ").append(NS_W).append(">");
        sb.append("<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>");
        sb.append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>");
        int[] sizes = {32, 26, 24, 22, 22, 22};
        for (int i = 0; i < 6; i++) {
            sb.append("<w:style w:type=\"paragraph\" w:styleId=\"Heading").append(i + 1).append("\">");
            sb.append("<w:name w:val=\"heading ").append(i + 1).append("\"/>");
            sb.append("<w:basedOn w:val=\"Normal\"/>");
            sb.append("<w:pPr><w:outlineLvl w:val=\"").append(i).append("\"/></w:pPr>");
            sb.append("<w:rPr><w:b/><w:sz w:val=\"").append(sizes[i]).append("\"/></w:rPr>");
            sb.append("</w:style>");
        }
        sb.append("</w:styles>");
        writeRaw(zip, sb.toString());
        zip.closeEntry();
    }

    /** 写出 word/media/imageN.ext 图片二进制。 */
    private static void writeImage(ZipOutputStream zip, Image img, int index) throws IOException {
        zip.putNextEntry(new ZipEntry("word/media/image" + index + "." + img.ext));
        zip.write(img.data);
        zip.closeEntry();
    }

    /** 转义 XML 特殊字符。 */
    static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
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

    /** 图片资源内部表示：id 与 rId 在 image() 时按实例内顺序分配（rId1 为 styles，图片自 rId2 起）。 */
    private static final class Image {
        final int id;
        final String rId;
        final String ext;
        final String contentType;
        final byte[] data;
        final long widthEmu;
        final long heightEmu;

        Image(int id, String rId, String mime, byte[] data, long widthEmu, long heightEmu) {
            this.id = id;
            this.rId = rId;
            this.data = data;
            this.widthEmu = widthEmu;
            this.heightEmu = heightEmu;
            String m = mime == null ? "" : mime.toLowerCase();
            switch (m) {
                case "image/jpeg", "image/jpg" -> { ext = "jpeg"; contentType = "image/jpeg"; }
                case "image/gif" -> { ext = "gif"; contentType = "image/gif"; }
                case "image/bmp" -> { ext = "bmp"; contentType = "image/bmp"; }
                default -> { ext = "png"; contentType = "image/png"; }
            }
        }
    }
}
