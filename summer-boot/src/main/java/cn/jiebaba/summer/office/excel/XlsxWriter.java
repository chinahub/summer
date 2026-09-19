package cn.jiebaba.summer.office.excel;

import cn.jiebaba.summer.office.OfficeException;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 流式 XLSX 写入器：纯 JDK 实现（java.util.zip），不依赖 Apache POI 或 FastExcel。
 * <p>仿 POI SXSSF 的流式设计，但简化为直接写入 ZipOutputStream：
 * <ul>
 *   <li>行数据逐行写入 ZIP 条目流，无需临时文件，内存占用 O(1) 每行</li>
 *   <li>字符串值累积到共享字符串表（{@code xl/sharedStrings.xml}），去重后写出</li>
 *   <li>样式累积到 {@link XlsxStyles}（自定义 numFmt + cellXfs 去重），{@code xl/styles.xml} 在 close() 时写出</li>
 *   <li>工作表静态部分（dimension/sheetViews/cols 在 sheetData 前，autoFilter/mergeCells 在 sheetData 后）按 OOXML 顺序写出</li>
 * </ul>
 * <p>支持单元格类型：字符串（共享字符串表）、数值、布尔、{@link Formula}（公式+可选缓存值）、
 * {@link ErrorValue}（错误码）、{@link LocalDate}/{@link LocalDateTime}/{@link LocalTime}/
 * {@link java.util.Date}/{@link Instant}（自动转 Excel 序列号并注册日期数字格式）。
 *
 * <pre>{@code
 * try (XlsxWriter writer = new XlsxWriter(out)) {
 *     writer.beginSheet("Sheet1")
 *           .setColumnWidth(0, 20)
 *           .freezePanes(0, 1)
 *           .writeRow("姓名", "生日", "金额")
 *           .writeRow("张三", LocalDate.of(2000, 1, 1), 95.5)
 *           .writeRow(new Formula("SUM(C2:C3)", 190.0))
 *           .mergeCells("A1:C1")
 *           .setAutoFilter("A1:C3")
 *           .endSheet();
 * } // close() 自动写出共享字符串、样式、工作簿、关系与内容类型
 * }</pre>
 */
public class XlsxWriter implements Closeable {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private final ZipOutputStream zip;
    private final XlsxStyles styles = new XlsxStyles();
    private final LinkedHashMap<String, Integer> sharedStrings = new LinkedHashMap<>();
    private final List<String> sheetNames = new ArrayList<>();
    /** 当前工作表列宽：列索引 -> 宽度（字符单位），须在写入首行前设置。 */
    private final Map<Integer, Double> colWidths = new LinkedHashMap<>();
    /** 当前工作表合并区域引用列表（如 "A1:C1"），在 endSheet 时写出。 */
    private final List<String> mergedRanges = new ArrayList<>();

    private int sharedStringRefCount;
    private int sheetIndex;
    private int rowIndex;
    private boolean sheetOpen;
    /** 工作表头部（sheetViews/cols/sheetData）是否待写入：beginSheet 置 true，首行或 endSheet 时刷出。 */
    private boolean headerPending;
    /** 当前工作表自动筛选区域引用，null 表示无。 */
    private String autoFilterRef;
    private int freezeCol;
    private int freezeRow;
    /** 待应用于下一行的行高（磅），0 表示默认。 */
    private double pendingRowHeight;
    /** 待应用于下一行的隐藏标记。 */
    private boolean pendingRowHidden;
    /** 待应用于下一行的大纲级别（0=无）。 */
    private int pendingOutlineLevel;
    /** 是否用内联字符串（t="inlineStr"）写出字符串，避免共享字符串表开销。 */
    private boolean inlineStrings;

    public XlsxWriter(OutputStream out) {
        this.zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    }

    /** 开始一个新工作表；若上一个工作表未关闭则自动关闭。列宽/冻结窗格须在写入首行前设置。 */
    public XlsxWriter beginSheet(String name) throws IOException {
        if (sheetOpen) {
            endSheet();
        }
        sheetIndex++;
        sheetNames.add(name == null ? "Sheet" + sheetIndex : name);
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + sheetIndex + ".xml"));
        writeRaw(XML_HEADER);
        writeRaw("<worksheet xmlns=\"" + SHEET_NS + "\">");
        writeRaw("<dimension ref=\"A1\"/>");
        colWidths.clear();
        mergedRanges.clear();
        autoFilterRef = null;
        freezeCol = 0;
        freezeRow = 0;
        pendingRowHeight = 0;
        pendingRowHidden = false;
        pendingOutlineLevel = 0;
        rowIndex = 0;
        headerPending = true;
        sheetOpen = true;
        return this;
    }

    /** 设置当前工作表指定列宽（字符单位）；须在写入首行前调用，否则抛出异常。 */
    public XlsxWriter setColumnWidth(int col, double width) {
        ensureSheet("设置列宽");
        if (!headerPending) {
            throw new OfficeException("列宽须在写入首行前设置");
        }
        colWidths.put(col, width);
        return this;
    }

    /** 冻结窗格：冻结前 colSplit 列与 rowSplit 行；须在写入首行前调用。 */
    public XlsxWriter freezePanes(int colSplit, int rowSplit) {
        ensureSheet("设置冻结窗格");
        if (!headerPending) {
            throw new OfficeException("冻结窗格须在写入首行前设置");
        }
        this.freezeCol = Math.max(0, colSplit);
        this.freezeRow = Math.max(0, rowSplit);
        return this;
    }

    /** 设置当前工作表自动筛选区域（如 "A1:C10"）；可在任意时刻调用，endSheet 时写出。 */
    public XlsxWriter setAutoFilter(String ref) {
        ensureSheet("设置自动筛选");
        this.autoFilterRef = ref;
        return this;
    }

    /** 合并单元格区域（如 "A1:C1"）；可在任意时刻调用，endSheet 时写出。 */
    public XlsxWriter mergeCells(String ref) {
        ensureSheet("合并单元格");
        mergedRanges.add(ref);
        return this;
    }

    /** 设置下一行的行高（磅）；每次 writeRow 后重置为默认。 */
    public XlsxWriter rowHeight(double points) {
        ensureSheet("设置行高");
        this.pendingRowHeight = points;
        return this;
    }

    /** 标记下一行为隐藏；每次 writeRow 后重置。 */
    public XlsxWriter hideRow() {
        ensureSheet("隐藏行");
        this.pendingRowHidden = true;
        return this;
    }

    /** 设置下一行的大纲级别（分组层级，0=无）；每次 writeRow 后重置。 */
    public XlsxWriter outlineLevel(int level) {
        ensureSheet("设置大纲级别");
        this.pendingOutlineLevel = Math.max(0, level);
        return this;
    }

    /** 启用/关闭内联字符串模式：开启后字符串以 t="inlineStr" 直接写出，不累积共享字符串表。 */
    public XlsxWriter inlineStrings(boolean inline) {
        this.inlineStrings = inline;
        return this;
    }

    /** 写入一行数据；每个元素按类型生成对应单元格（字符串/数值/布尔/日期/公式/错误）。 */
    public XlsxWriter writeRow(Object... cells) throws IOException {
        return writeRow(cells == null ? List.of() : Arrays.asList(cells));
    }

    /** 写入一行数据（List 版本）；字符串去重到共享字符串表，日期转序列号并注册样式，首行前刷出工作表头部。 */
    public XlsxWriter writeRow(List<?> cells) throws IOException {
        ensureSheet("写入行");
        flushSheetHeader();
        rowIndex++;
        int row = rowIndex;
        StringBuilder sb = new StringBuilder(64 + cells.size() * 32);
        sb.append("<row r=\"").append(row).append("\"");
        if (pendingRowHeight > 0) {
            sb.append(" ht=\"").append(formatWidth(pendingRowHeight)).append("\" customHeight=\"1\"");
            pendingRowHeight = 0;
        }
        if (pendingRowHidden) {
            sb.append(" hidden=\"1\"");
            pendingRowHidden = false;
        }
        if (pendingOutlineLevel > 0) {
            sb.append(" outlineLevel=\"").append(pendingOutlineLevel).append("\"");
            pendingOutlineLevel = 0;
        }
        sb.append(">");
        int col = 0;
        for (Object cell : cells) {
            sb.append(toCellXml(col, row, cell));
            col++;
        }
        sb.append("</row>");
        writeRaw(sb.toString());
        return this;
    }

    /** 结束当前工作表：刷出未写的头部、关闭 sheetData，写出 autoFilter 与 mergeCells，关闭 ZIP 条目。 */
    public XlsxWriter endSheet() throws IOException {
        if (!sheetOpen) {
            return this;
        }
        flushSheetHeader();
        writeRaw("</sheetData>");
        String post = buildPostSheetData();
        if (!post.isEmpty()) {
            writeRaw(post);
        }
        writeRaw("</worksheet>");
        zip.closeEntry();
        sheetOpen = false;
        return this;
    }

    /** 关闭写入器：结束未关闭的工作表，依次写出共享字符串、样式、工作簿、关系与内容类型，最后关闭 ZipOutputStream。 */
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

    // ==================== 工作表头部与尾部构造 ====================

    /** 刷出待写的工作表头部：sheetViews（冻结窗格）、cols（列宽）、sheetData 开始标签。 */
    private void flushSheetHeader() throws IOException {
        if (!headerPending) {
            return;
        }
        writeRaw(buildSheetViews());
        String cols = buildCols();
        if (!cols.isEmpty()) {
            writeRaw(cols);
        }
        writeRaw("<sheetData>");
        headerPending = false;
    }

    /** 构造 sheetViews：含冻结窗格 pane/selection，否则仅默认 sheetView。 */
    private String buildSheetViews() {
        if (freezeCol <= 0 && freezeRow <= 0) {
            return "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">");
        if (freezeCol > 0 && freezeRow > 0) {
            String cell = toColumnLetter(freezeCol) + (freezeRow + 1);
            sb.append("<pane xSplit=\"").append(freezeCol).append("\" ySplit=\"").append(freezeRow)
                    .append("\" topLeftCell=\"").append(cell).append("\" activePane=\"bottomRight\" state=\"frozen\"/>");
            sb.append("<selection pane=\"bottomRight\" activeCell=\"").append(cell)
                    .append("\" sqref=\"").append(cell).append("\"/>");
        } else if (freezeRow > 0) {
            String cell = "A" + (freezeRow + 1);
            sb.append("<pane ySplit=\"").append(freezeRow)
                    .append("\" topLeftCell=\"").append(cell).append("\" activePane=\"bottomLeft\" state=\"frozen\"/>");
            sb.append("<selection pane=\"bottomLeft\" activeCell=\"").append(cell)
                    .append("\" sqref=\"").append(cell).append("\"/>");
        } else {
            String col = toColumnLetter(freezeCol);
            String cell = col + "1";
            sb.append("<pane xSplit=\"").append(freezeCol)
                    .append("\" topLeftCell=\"").append(cell).append("\" activePane=\"topRight\" state=\"frozen\"/>");
            sb.append("<selection pane=\"topRight\" activeCell=\"").append(cell)
                    .append("\" sqref=\"").append(cell).append("\"/>");
        }
        sb.append("</sheetView></sheetViews>");
        return sb.toString();
    }

    /** 构造 cols：每列一条 col 元素（min=max=col+1，customWidth=1）。 */
    private String buildCols() {
        if (colWidths.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append("<cols>");
        for (Map.Entry<Integer, Double> e : colWidths.entrySet()) {
            int col = e.getKey() + 1;
            sb.append("<col min=\"").append(col).append("\" max=\"").append(col)
                    .append("\" width=\"").append(formatWidth(e.getValue())).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols>");
        return sb.toString();
    }

    /** 构造 sheetData 之后的元素：autoFilter 在前，mergeCells 在后（遵循 OOXML CTWorksheet 顺序）。 */
    private String buildPostSheetData() {
        StringBuilder sb = new StringBuilder(64);
        if (autoFilterRef != null) {
            sb.append("<autoFilter ref=\"").append(autoFilterRef).append("\"/>");
        }
        if (!mergedRanges.isEmpty()) {
            sb.append("<mergeCells count=\"").append(mergedRanges.size()).append("\">");
            for (String r : mergedRanges) {
                sb.append("<mergeCell ref=\"").append(r).append("\"/>");
            }
            sb.append("</mergeCells>");
        }
        return sb.toString();
    }

    // ==================== 单元格 XML 生成 ====================

    /** 根据值类型生成单元格 XML：公式/错误/日期/数值/布尔/字符串分别处理，null 生成空单元格。 */
    private String toCellXml(int col, int row, Object value) {
        String ref = toCellRef(col, row);
        if (value == null) {
            return "<c r=\"" + ref + "\"/>";
        }
        if (value instanceof Formula f) {
            return formulaCellXml(ref, f);
        }
        if (value instanceof ErrorValue e) {
            return "<c r=\"" + ref + "\" t=\"e\"><v>" + escapeXml(e.code()) + "</v></c>";
        }
        if (value instanceof LocalDate d) {
            return dateCellXml(ref, "yyyy-MM-dd", XlsxStyles.toSerial(d));
        }
        if (value instanceof LocalDateTime d) {
            return dateCellXml(ref, "yyyy-MM-dd HH:mm:ss", XlsxStyles.toSerial(d));
        }
        if (value instanceof LocalTime d) {
            double frac = (d.toSecondOfDay() + d.getNano() / 1_000_000_000.0) / 86400.0;
            return dateCellXml(ref, "HH:mm:ss", frac);
        }
        if (value instanceof java.util.Date d) {
            LocalDateTime dt = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
            return dateCellXml(ref, "yyyy-MM-dd HH:mm:ss", XlsxStyles.toSerial(dt));
        }
        if (value instanceof Instant d) {
            LocalDateTime dt = LocalDateTime.ofInstant(d, ZoneOffset.UTC);
            return dateCellXml(ref, "yyyy-MM-dd HH:mm:ss", XlsxStyles.toSerial(dt));
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
        if (inlineStrings) {
            return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                    + escapeXml(str) + "</t></is></c>";
        }
        int idx = sharedStrings.computeIfAbsent(str, k -> sharedStrings.size());
        sharedStringRefCount++;
        return "<c r=\"" + ref + "\" t=\"s\"><v>" + idx + "</v></c>";
    }

    /** 生成日期单元格 XML：注册日期 numFmt 样式，写出 s 引用与序列号 v。 */
    private String dateCellXml(String ref, String formatCode, double serial) {
        int styleIndex = styles.registerDateStyle(formatCode);
        return "<c r=\"" + ref + "\" s=\"" + styleIndex + "\"><v>" + formatNumber(serial) + "</v></c>";
    }

    /** 生成公式单元格 XML：依据缓存值类型设置 t 属性与 v 标签，无缓存值时仅写 f。 */
    private String formulaCellXml(String ref, Formula f) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("<c r=\"").append(ref).append("\"");
        Object cached = f.cachedValue();
        String valueXml = null;
        if (cached instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                valueXml = "<v>" + formatNumber(d) + "</v>";
            }
        } else if (cached instanceof Boolean b) {
            sb.append(" t=\"b\"");
            valueXml = "<v>" + (b ? 1 : 0) + "</v>";
        } else if (cached instanceof ErrorValue e) {
            sb.append(" t=\"e\"");
            valueXml = "<v>" + escapeXml(e.code()) + "</v>";
        } else if (cached instanceof CharSequence s) {
            sb.append(" t=\"str\"");
            valueXml = "<v>" + escapeXml(s.toString()) + "</v>";
        }
        sb.append("><f>").append(escapeXml(f.formula())).append("</f>");
        if (valueXml != null) {
            sb.append(valueXml);
        }
        sb.append("</c>");
        return sb.toString();
    }

    /** 将列索引与行索引转为 A1 引用（如 0,1 -> "A1"；25,1 -> "Z1"；26,1 -> "AA1"）。 */
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

    /** 格式化数字：整数值不带小数点，浮点值保留原值。 */
    private static String formatNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /** 格式化宽度/行高：整数值不带小数，否则保留两位（Locale.ROOT 避免逗号小数点）。 */
    private static String formatWidth(double w) {
        if (w == Math.rint(w)) {
            return String.valueOf((long) w);
        }
        return String.format(Locale.ROOT, "%.2f", w);
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

    private void ensureSheet(String action) {
        if (!sheetOpen) {
            throw new OfficeException("未调用 beginSheet，无法" + action);
        }
    }

    // ==================== 静态部分写入 ====================

    private void writeSharedStrings() throws IOException {
        zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
        StringBuilder sb = new StringBuilder(256);
        sb.append(XML_HEADER);
        sb.append("<sst xmlns=\"").append(SHEET_NS).append("\"");
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
        writeRaw(styles.toXml());
        zip.closeEntry();
    }

    private void writeWorkbook() throws IOException {
        zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
        StringBuilder sb = new StringBuilder(256);
        sb.append(XML_HEADER);
        sb.append("<workbook xmlns=\"").append(SHEET_NS).append("\"");
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
