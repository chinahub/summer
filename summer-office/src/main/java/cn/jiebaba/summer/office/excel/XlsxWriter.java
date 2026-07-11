package cn.jiebaba.summer.office.excel;

import cn.jiebaba.summer.office.OfficeException;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 流式 XLSX 写入器：纯 JDK 实现（java.util.zip），不依赖 Apache POI 或 FastExcel。
 * <p>仿 POI SXSSF 的流式设计，但简化为直接写入 ZipOutputStream：
 * <ul>
 *   <li>行数据逐行写入 ZIP 条目流，无需临时文件，内存占用 O(1) 每行</li>
 *   <li>字符串值累积到共享字符串表（{@code xl/sharedStrings.xml}），去重后写出</li>
 *   <li>静态部分（Content_Types、workbook.xml、styles.xml 等）在 {@link #close()} 时统一写出</li>
 * </ul>
 * XLSX 本质为 ZIP + OOXML XML，结构：{@code [Content_Types].xml}、{@code xl/workbook.xml}、
 * {@code xl/worksheets/sheetN.xml}、{@code xl/sharedStrings.xml}、{@code xl/styles.xml}。
 *
 * <pre>{@code
 * try (XlsxWriter writer = new XlsxWriter(out)) {
 *     writer.beginSheet("Sheet1");
 *     writer.writeRow("姓名", "年龄", "分数");
 *     writer.writeRow("张三", 25, 95.5);
 *     writer.endSheet();
 * } // close() 自动写出共享字符串与静态部分
 * }</pre>
 */
public class XlsxWriter implements Closeable {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    private final ZipOutputStream zip;
    private final LinkedHashMap<String, Integer> sharedStrings = new LinkedHashMap<>();
    private final List<String> sheetNames = new ArrayList<>();
    private int sharedStringRefCount;
    private int sheetIndex;
    private int rowIndex;
    private boolean sheetOpen;

    public XlsxWriter(OutputStream out) {
        this.zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    }

    /** 开始一个新工作表；若上一个工作表未关闭则自动关闭。 */
    public XlsxWriter beginSheet(String name) throws IOException {
        if (sheetOpen) {
            endSheet();
        }
        sheetIndex++;
        sheetNames.add(name == null ? "Sheet" + sheetIndex : name);
        String entry = "xl/worksheets/sheet" + sheetIndex + ".xml";
        zip.putNextEntry(new ZipEntry(entry));
        writeRaw(XML_HEADER);
        writeRaw("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        writeRaw("<sheetData>");
        rowIndex = 0;
        sheetOpen = true;
        return this;
    }

    /** 写入一行数据；每个元素按类型生成对应单元格（字符串/数字/布尔）。 */
    public XlsxWriter writeRow(Object... cells) throws IOException {
        return writeRow(cells == null ? List.of() : Arrays.asList(cells));
    }

    /** 写入一行数据（List 版本）；字符串去重到共享字符串表，数字直接写入，空值生成空单元格。 */
    public XlsxWriter writeRow(List<?> cells) throws IOException {
        if (!sheetOpen) {
            throw new OfficeException("未调用 beginSheet，无法写入行");
        }
        rowIndex++;
        int row = rowIndex;
        StringBuilder sb = new StringBuilder(64 + cells.size() * 32);
        sb.append("<row r=\"").append(row).append("\">");
        int col = 0;
        for (Object cell : cells) {
            sb.append(toCellXml(col, row, cell));
            col++;
        }
        sb.append("</row>");
        writeRaw(sb.toString());
        return this;
    }

    /** 结束当前工作表，关闭 sheetData 与 worksheet 标签及 ZIP 条目。 */
    public XlsxWriter endSheet() throws IOException {
        if (!sheetOpen) {
            return this;
        }
        writeRaw("</sheetData></worksheet>");
        zip.closeEntry();
        sheetOpen = false;
        return this;
    }

    /**
     * 关闭写入器：结束未关闭的工作表，依次写出共享字符串、样式、工作簿、关系与内容类型，
     * 最后关闭 ZipOutputStream。调用后输出的字节即为完整的 .xlsx 文件。
     */
    @Override
    public void close() throws IOException {
        if (sheetOpen) {
            endSheet();
        }
        writeSharedStrings();
        writeStyles();
        writeWorkbook();
        writeWorkbookRels();
        writeRootRels();
        writeContentTypes();
        zip.close();
    }

    // ==================== 单元格 XML 生成 ====================

    /** 根据值类型生成单元格 XML：字符串->共享字符串引用，数字->数值，布尔->0/1，null->空单元格。 */
    private String toCellXml(int col, int row, Object value) {
        String ref = toCellRef(col, row);
        if (value == null) {
            return "<c r=\"" + ref + "\"/>";
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "<c r=\"" + ref + "\"/>";
            }
            return "<c r=\"" + ref + "\"><v>" + formatNumber(d) + "</v></c>";
        }
        if (value instanceof Boolean b) {
            return "<c r=\"" + ref + "\" t=\"b\"><v>" + (b ? 1 : 0) + "</v></c>";
        }
        String str = value.toString();
        int idx = sharedStrings.computeIfAbsent(str, k -> sharedStrings.size());
        sharedStringRefCount++;
        return "<c r=\"" + ref + "\" t=\"s\"><v>" + idx + "</v></c>";
    }

    /** 将列索引与行索引转为 A1 引用（如 0,0 -> "A1"；25,0 -> "Z1"；26,0 -> "AA1"）。 */
    static String toCellRef(int col, int row) {
        return toColumnLetter(col) + (row);
    }

    /** 将 0 基列索引转为字母（0->A, 25->Z, 26->AA）。 */
    static String toColumnLetter(int col) {
        StringBuilder sb = new StringBuilder();
        int n = col + 1;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return sb.toString();
    }

    /** 格式化数字：整数值不带小数点，浮点值保留有效位。 */
    private static String formatNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /** XML 转义：&, <, >, ", ' 及控制字符。 */
    static String escapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c >= 32 || c == '\n' || c == '\r' || c == '\t') {
                        sb.append(c);
                    } else {
                        sb.append('?');
                    }
                }
            }
        }
        return sb.toString();
    }

    // ==================== 静态部分写入 ====================

    private void writeSharedStrings() throws IOException {
        zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
        StringBuilder sb = new StringBuilder(256);
        sb.append(XML_HEADER);
        sb.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"");
        sb.append(" count=\"").append(sharedStringRefCount).append("\"");
        sb.append(" uniqueCount=\"").append(sharedStrings.size()).append("\">");
        for (String s : sharedStrings.keySet()) {
            sb.append("<si><t xml:space=\"preserve\">").append(escapeXml(s)).append("</t></si>");
        }
        sb.append("</sst>");
        writeRaw(sb.toString());
        zip.closeEntry();
    }

    private void writeStyles() throws IOException {
        zip.putNextEntry(new ZipEntry("xl/styles.xml"));
        writeRaw(XML_HEADER);
        writeRaw("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        writeRaw("<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>");
        writeRaw("</styleSheet>");
        zip.closeEntry();
    }

    private void writeWorkbook() throws IOException {
        zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
        StringBuilder sb = new StringBuilder(256);
        sb.append(XML_HEADER);
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"");
        sb.append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        sb.append("<sheets>");
        for (int i = 0; i < sheetNames.size(); i++) {
            sb.append("<sheet name=\"").append(escapeXml(sheetNames.get(i))).append("\"");
            sb.append(" sheetId=\"").append(i + 1).append("\"");
            sb.append(" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets></workbook>");
        writeRaw(sb.toString());
        zip.closeEntry();
    }

    /** 写出工作簿关系文件 xl/_rels/workbook.xml.rels：每个工作表一条关系，附加样式与共享字符串关系。 */
    private void writeWorkbookRels() throws IOException {
        zip.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
        StringBuilder sb = new StringBuilder(256);
        sb.append(XML_HEADER);
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheetNames.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1).append("\"");
            sb.append(" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"");
            sb.append(" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        int stylesId = sheetNames.size() + 1;
        int sharedStringsId = sheetNames.size() + 2;
        sb.append("<Relationship Id=\"rId").append(stylesId).append("\"");
        sb.append(" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\"");
        sb.append(" Target=\"styles.xml\"/>");
        sb.append("<Relationship Id=\"rId").append(sharedStringsId).append("\"");
        sb.append(" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\"");
        sb.append(" Target=\"sharedStrings.xml\"/>");
        sb.append("</Relationships>");
        writeRaw(sb.toString());
        zip.closeEntry();
    }

    private void writeRootRels() throws IOException {
        zip.putNextEntry(new ZipEntry("_rels/.rels"));
        writeRaw(XML_HEADER);
        writeRaw("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        writeRaw("<Relationship Id=\"rId1\"");
        writeRaw(" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"");
        writeRaw(" Target=\"xl/workbook.xml\"/>");
        writeRaw("</Relationships>");
        zip.closeEntry();
    }

    private void writeContentTypes() throws IOException {
        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        StringBuilder sb = new StringBuilder(512);
        sb.append(XML_HEADER);
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 0; i < sheetNames.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1).append(".xml\"");
            sb.append(" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        sb.append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>");
        sb.append("</Types>");
        writeRaw(sb.toString());
        zip.closeEntry();
    }

    private void writeRaw(String text) throws IOException {
        zip.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
