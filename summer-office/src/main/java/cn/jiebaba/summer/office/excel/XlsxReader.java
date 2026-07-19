package cn.jiebaba.summer.office.excel;

import cn.jiebaba.summer.office.OfficeException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 流式 XLSX 读取器：纯 JDK 实现（java.util.zip + SAX），不依赖 Apache POI 或 FastExcel。
 * <p>仿 POI 的 SAX 事件驱动读取，逐行回调 {@link RowHandler}，内存占用 O(1) 每行：
 * <ol>
 *   <li>将输入流拷贝到临时文件（ZipFile 需随机访问条目）</li>
 *   <li>StAX 解析 {@code xl/workbook.xml} 与 {@code xl/_rels/workbook.xml.rels} 获取工作表映射</li>
 *   <li>StAX 解析 {@code xl/sharedStrings.xml} 构建共享字符串表（含富文本拼接）</li>
 *   <li>StAX 解析 {@code xl/styles.xml} 构建 numFmt 与 cellXfs（用于日期识别）</li>
 *   <li>SAX 解析目标工作表 XML，逐行回调 RowHandler</li>
 * </ol>
 * <p>单元格值解析（借鉴 POI DataFormatter）：共享字符串按索引查表；布尔转 TRUE/FALSE；
 * 错误码原样返回；公式单元格返回缓存值（日期样式则格式化），无缓存值时返回公式文本；
 * 数值若为日期样式则序列号转日期字符串，避免出现 45658 这类原始序列号。
 *
 * <pre>{@code
 * try (XlsxReader reader = new XlsxReader(inputStream)) {
 *     reader.read((rowIndex, cells) -> {
 *         System.out.println("Row " + rowIndex + ": " + cells);
 *     });
 * } // close() 自动删除临时文件
 * }</pre>
 */
public class XlsxReader implements Closeable {

    private final ZipFile zip;
    private final Path tempFile;
    private final List<String> sharedStrings;
    private final List<String[]> sheetMeta;
    private final Map<String, String> rels;
    private final XlsxStyles styles;

    /** 从输入流构造读取器；输入流会被拷贝到临时文件，构造后即可关闭输入流。 */
    public XlsxReader(InputStream in) throws IOException {
        try {
            tempFile = Files.createTempFile("summer-xlsx-read-", ".xlsx");
            try (InputStream src = in; var out = Files.newOutputStream(tempFile)) {
                src.transferTo(out);
            }
            zip = new ZipFile(tempFile.toFile());
            sheetMeta = parseWorkbook();
            rels = parseRels();
            sharedStrings = parseSharedStrings();
            styles = parseStyles();
        } catch (XMLStreamException e) {
            throw new OfficeException("XLSX 解析失败", e);
        }
    }

    /** 读取第一个工作表，逐行回调 handler。 */
    public void read(RowHandler handler) throws IOException {
        read(0, handler);
    }

    /** 读取指定索引的工作表（从 0 开始），逐行回调 handler。 */
    public void read(int sheetIndex, RowHandler handler) throws IOException {
        if (sheetIndex < 0 || sheetIndex >= sheetMeta.size()) {
            throw new OfficeException("工作表索引越界：" + sheetIndex + "，共 " + sheetMeta.size() + " 个工作表");
        }
        String rId = sheetMeta.get(sheetIndex)[1];
        String target = rels.get(rId);
        if (target == null) {
            throw new OfficeException("未找到工作表关系：" + rId);
        }
        String entry = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        ZipEntry zipEntry = zip.getEntry(entry);
        if (zipEntry == null) {
            throw new OfficeException("未找到工作表文件：" + entry);
        }
        try (InputStream sheetIn = zip.getInputStream(zipEntry)) {
            SAXParser parser = createSaxParser();
            parser.parse(sheetIn, new WorksheetHandler(sharedStrings, styles, handler));
        } catch (SAXException e) {
            throw new OfficeException("工作表 SAX 解析失败", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
        }
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    // ==================== StAX 解析：工作簿、共享字符串、样式 ====================

    /** StAX 解析 xl/workbook.xml，提取工作表名与关系 ID 列表。 */
    private List<String[]> parseWorkbook() throws IOException, XMLStreamException {
        ZipEntry entry = zip.getEntry("xl/workbook.xml");
        if (entry == null) {
            throw new OfficeException("缺少 xl/workbook.xml");
        }
        List<String[]> sheets = new ArrayList<>();
        XMLStreamReader reader = createStaxReader(zip.getInputStream(entry));
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "sheet".equals(reader.getLocalName())) {
                String name = null;
                String rId = null;
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    String ln = reader.getAttributeLocalName(i);
                    if ("name".equals(ln)) {
                        name = reader.getAttributeValue(i);
                    } else if ("id".equals(ln)) {
                        rId = reader.getAttributeValue(i);
                    }
                }
                sheets.add(new String[]{name, rId});
            }
        }
        return sheets;
    }

    /** StAX 解析 xl/_rels/workbook.xml.rels，构建关系 ID -> 工作表目标路径映射。 */
    private Map<String, String> parseRels() throws IOException, XMLStreamException {
        ZipEntry entry = zip.getEntry("xl/_rels/workbook.xml.rels");
        Map<String, String> map = new HashMap<>();
        if (entry == null) {
            return map;
        }
        XMLStreamReader reader = createStaxReader(zip.getInputStream(entry));
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "Relationship".equals(reader.getLocalName())) {
                String id = null;
                String target = null;
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    if ("Id".equals(reader.getAttributeLocalName(i))) {
                        id = reader.getAttributeValue(i);
                    } else if ("Target".equals(reader.getAttributeLocalName(i))) {
                        target = reader.getAttributeValue(i);
                    }
                }
                if (id != null && target != null) {
                    map.put(id, target);
                }
            }
        }
        return map;
    }

    /** StAX 解析 xl/sharedStrings.xml，构建共享字符串表；富文本 <r><t> 段落自动拼接。 */
    private List<String> parseSharedStrings() throws IOException, XMLStreamException {
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        List<String> list = new ArrayList<>();
        if (entry == null) {
            return list;
        }
        XMLStreamReader reader = createStaxReader(zip.getInputStream(entry));
        StringBuilder current = new StringBuilder();
        boolean inSi = false;
        boolean inT = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                switch (name) {
                    case "si" -> {
                        inSi = true;
                        current.setLength(0);
                    }
                    case "t" -> {
                        inT = true;
                        current.append(reader.getElementText());
                        inT = false;
                    }
                    default -> { }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName())) {
                if (inSi) {
                    list.add(current.toString());
                    inSi = false;
                }
            }
        }
        return list;
    }

    /** StAX 解析 xl/styles.xml 构建 {@link XlsxStyles}；样式缺失时返回空样式（所有单元格按数值处理）。 */
    private XlsxStyles parseStyles() throws IOException, XMLStreamException {
        XlsxStyles table = new XlsxStyles();
        ZipEntry entry = zip.getEntry("xl/styles.xml");
        if (entry != null) {
            try (InputStream in = zip.getInputStream(entry)) {
                table.parse(in);
            }
        }
        return table;
    }

    private XMLStreamReader createStaxReader(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory.createXMLStreamReader(in);
    }

    private SAXParser createSaxParser() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newSAXParser();
        } catch (Exception e) {
            throw new OfficeException("SAX 解析器创建失败", e);
        }
    }

    // ==================== SAX 工作表处理器 ====================

    /** SAX 事件处理器：逐行解析工作表 XML，按单元格类型与样式解析值，每行结束后回调 RowHandler。 */
    private static class WorksheetHandler extends DefaultHandler {

        private final List<String> sharedStrings;
        private final XlsxStyles styles;
        private final RowHandler rowHandler;

        private List<String> currentRow;
        private int rowNum;
        private int colIndex;
        private String cellType;
        private int styleIndex;
        private StringBuilder textBuilder;
        private boolean inValue;
        private boolean inInlineString;
        private boolean inFormula;
        private StringBuilder formulaBuilder;

        WorksheetHandler(List<String> sharedStrings, XlsxStyles styles, RowHandler rowHandler) {
            this.sharedStrings = sharedStrings;
            this.styles = styles;
            this.rowHandler = rowHandler;
        }

        /** SAX 元素开始事件：按标签名处理 row/c/v/is/t/f，记录行号、列索引、单元格类型、样式与值捕获状态。 */
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (qName) {
                case "row" -> {
                    currentRow = new ArrayList<>();
                    String r = attributes.getValue("r");
                    rowNum = r != null ? Integer.parseInt(r) - 1 : -1;
                }
                case "c" -> {
                    String ref = attributes.getValue("r");
                    colIndex = ref != null ? refToCol(ref) : currentRow.size();
                    cellType = attributes.getValue("t");
                    String s = attributes.getValue("s");
                    styleIndex = s != null ? Integer.parseInt(s) : 0;
                    while (currentRow.size() < colIndex) {
                        currentRow.add("");
                    }
                    textBuilder = null;
                    formulaBuilder = null;
                }
                case "v" -> {
                    textBuilder = new StringBuilder();
                    inValue = true;
                }
                case "f" -> {
                    inFormula = true;
                    formulaBuilder = new StringBuilder();
                }
                case "is" -> inInlineString = true;
                case "t" -> {
                    if (inInlineString) {
                        textBuilder = new StringBuilder();
                        inValue = true;
                    }
                }
                default -> { }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue && textBuilder != null) {
                textBuilder.append(ch, start, length);
            } else if (inFormula && formulaBuilder != null) {
                formulaBuilder.append(ch, start, length);
            }
        }

        /** SAX 元素结束事件：按标签名处理 c/row，依据类型与样式解析单元格值并回调行处理器。 */
        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case "v" -> inValue = false;
                case "f" -> inFormula = false;
                case "t" -> {
                    if (inInlineString) {
                        inValue = false;
                    }
                }
                case "is" -> inInlineString = false;
                case "c" -> {
                    String value = resolveCellValue();
                    while (currentRow.size() <= colIndex) {
                        currentRow.add("");
                    }
                    currentRow.set(colIndex, value);
                    textBuilder = null;
                    formulaBuilder = null;
                    cellType = null;
                    styleIndex = 0;
                }
                case "row" -> {
                    if (rowNum < 0) {
                        rowNum++;
                    }
                    rowHandler.handle(rowNum, currentRow);
                }
                default -> { }
            }
        }

        /** 解析单元格值：共享字符串查表；布尔转 TRUE/FALSE；错误/字符串结果原样；数值按日期样式格式化；公式无缓存值时返回公式文本。 */
        private String resolveCellValue() {
            String raw = textBuilder == null ? null : textBuilder.toString();
            boolean hasValue = raw != null && !raw.isEmpty();
            if ("s".equals(cellType)) {
                if (!hasValue) {
                    return "";
                }
                try {
                    int idx = Integer.parseInt(raw);
                    return idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
                } catch (NumberFormatException e) {
                    return "";
                }
            }
            if ("b".equals(cellType)) {
                return "1".equals(raw) ? "TRUE" : "FALSE";
            }
            if ("e".equals(cellType) || "str".equals(cellType)) {
                return hasValue ? raw : "";
            }
            if (hasValue) {
                if ("inlineStr".equals(cellType)) {
                    return raw;
                }
                if (styles.isDateFormat(styleIndex)) {
                    try {
                        String formatted = styles.formatValue(styleIndex, Double.parseDouble(raw));
                        if (formatted != null) {
                            return formatted;
                        }
                    } catch (NumberFormatException e) {
                        return raw;
                    }
                }
                return raw;
            }
            if (formulaBuilder != null && !formulaBuilder.isEmpty()) {
                return formulaBuilder.toString();
            }
            return "";
        }
    }

    /** 将 A1 引用的列字母部分转为 0 基列索引（"A"->0, "Z"->25, "AA"->26）。 */
    static int refToCol(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else {
                break;
            }
        }
        return col - 1;
    }
}
