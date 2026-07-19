package cn.jiebaba.summer.office.excel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * XLSX 样式与日期处理：纯 JDK 实现，借鉴 Apache POI 的 StylesTable + DateUtil + DataFormatter 思路。
 * <p>读取侧：StAX 解析 {@code xl/styles.xml} 的 numFmts 与 cellXfs，按 numFmtId 判断日期格式，
 * 将 Excel 序列号转为 {@link LocalDate}/{@link LocalDateTime} 并渲染为字符串。
 * <p>写入侧：累积自定义 numFmt 与 cellXfs（去重），生成 styles.xml；提供注册日期样式并返回样式索引。
 * <p>Excel 1900 日期系统存在伪 1900-02-29 闰日 bug，本类以 1899-12-30 为基准，
 * 对 1900-03-01（序列号 61）之后的所有真实日期精确，覆盖全部常见数据场景。
 * <p>序列号转换已对照 POI 5.4.1 {@code DateUtil.getExcelDate/getLocalDateTime} 验证一致；
 * 内置日期格式 id 与 {@code DateUtil.isInternalDateFormat} 一致（14-22、45-47）。
 */
final class XlsxStyles {

    /** Excel 1900 日期系统基准（修正伪闰日，序列号 61=1900-03-01 起精确）。 */
    private static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);

    /** 一天的纳秒数，用于序列号小数分量转时间。 */
    private static final long NANOS_PER_DAY = 86400_000_000_000L;

    /** POI 5.4.1 DateUtil.isInternalDateFormat 返回 true 的内置 numFmtId 集合（14-22 为日期/时间，45-47 为分秒）。 */
    private static final Set<Integer> BUILTIN_DATE_FORMATS = Set.of(
            14, 15, 16, 17, 18, 19, 20, 21, 22,
            45, 46, 47);

    /** 自定义 numFmtId 起始值（OOXML 规定 164 起为用户自定义）。 */
    private static final int FIRST_CUSTOM_FMT_ID = 164;

    // ==================== 读取侧状态 ====================

    /** 读取侧：自定义 numFmtId -> 格式串。 */
    private final Map<Integer, String> readNumFmts = new HashMap<>();
    /** 读取侧：cellXfs 列表，每项为该 xf 引用的 numFmtId（缺省 0）。 */
    private final List<Integer> readCellXfs = new ArrayList<>();

    // ==================== 写入侧状态 ====================

    /** 写入侧：自定义格式串 -> numFmtId（自 164 起，去重）。 */
    private final Map<String, Integer> writeNumFmts = new LinkedHashMap<>();
    /** 写入侧：cellXfs 的 numFmtId 列表（index 0 为默认样式）。 */
    private final List<Integer> writeCellXfs = new ArrayList<>();
    /** 写入侧：numFmtId -> cellXfs 索引（去重缓存）。 */
    private final Map<Integer, Integer> xfIndexCache = new HashMap<>();
    /** 写入侧：下一个自定义 numFmtId。 */
    private int nextCustomFmtId = FIRST_CUSTOM_FMT_ID;

    XlsxStyles() {
        // 默认 xf（index 0），numFmtId=0（General）
        writeCellXfs.add(0);
        xfIndexCache.put(0, 0);
    }

    // ==================== 读取侧：解析 styles.xml ====================

    /** StAX 解析 xl/styles.xml，提取 numFmts 与 cellXfs 的 numFmtId；样式缺失时状态保持空。 */
    void parse(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XMLStreamReader r = factory.createXMLStreamReader(in);
        String section = null;
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                switch (name) {
                    case "numFmts" -> section = "numFmts";
                    case "cellXfs" -> section = "cellXfs";
                    case "numFmt" -> {
                        String id = r.getAttributeValue(null, "numFmtId");
                        String code = r.getAttributeValue(null, "formatCode");
                        if (id != null && code != null) {
                            readNumFmts.put(Integer.parseInt(id), code);
                        }
                    }
                    case "xf" -> {
                        if ("cellXfs".equals(section)) {
                            String id = r.getAttributeValue(null, "numFmtId");
                            readCellXfs.add(id != null ? Integer.parseInt(id) : 0);
                        }
                    }
                    default -> { }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = r.getLocalName();
                if ("numFmts".equals(name) || "cellXfs".equals(name)) {
                    section = null;
                }
            }
        }
    }

    /** 判断指定样式索引（cellXfs 下标）是否为日期/时间格式；越界或无样式返回 false。 */
    boolean isDateFormat(int styleIndex) {
        if (styleIndex < 0 || styleIndex >= readCellXfs.size()) {
            return false;
        }
        int numFmtId = readCellXfs.get(styleIndex);
        if (BUILTIN_DATE_FORMATS.contains(numFmtId)) {
            return true;
        }
        return isDateFormatString(readNumFmts.get(numFmtId));
    }

    /**
     * 判断 numFmt 格式串是否为日期/时间格式（借鉴 POI DateUtil.isADateFormat）。
     * 去除引号字面量、反斜杠转义、方括号段（如 [Red]）后，检查是否含 y/d（日期）、h/s（时间）或 m（月/分）占位符。
     * <p>不把 e/E 视为日期占位：e/E 在 "General"/"0.00E+00" 等非日期格式中常见会导致误判，
     * POI 以正则上下文区分 locale-era 的 e，本实现为避免误判直接忽略 e/E（locale-era 场景极少）。
     */
    static boolean isDateFormatString(String fmt) {
        if (fmt == null || fmt.isEmpty()) {
            return false;
        }
        String s = fmt
                .replaceAll("\"[^\"]*\"", "")
                .replaceAll("'[^']*'", "")
                .replaceAll("\\\\.", "")
                .replaceAll("\\[[^]]*]", "");
        boolean hasDate = false;
        boolean hasTime = false;
        boolean hasMonth = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("yYdD".indexOf(c) >= 0) {
                hasDate = true;
            } else if ("hHsS".indexOf(c) >= 0) {
                hasTime = true;
            } else if (c == 'm' || c == 'M') {
                hasMonth = true;
            }
        }
        return hasDate || hasTime || hasMonth;
    }

    /** 按样式索引与序列号渲染日期/时间为字符串；非日期格式返回 null 交由调用方按数值处理。 */
    String formatValue(int styleIndex, double serial) {
        if (!isDateFormat(styleIndex)) {
            return null;
        }
        String fmt = null;
        if (styleIndex >= 0 && styleIndex < readCellXfs.size()) {
            fmt = readNumFmts.get(readCellXfs.get(styleIndex));
        }
        return formatDateTime(serial, fmt);
    }

    /** 将 Excel 序列号转为 LocalDate（丢弃时间分量）。 */
    static LocalDate serialToDate(double serial) {
        return EPOCH.plusDays((long) Math.floor(serial));
    }

    /** 将 Excel 序列号转为 LocalDateTime（含时间分量）。 */
    static LocalDateTime serialToDateTime(double serial) {
        long days = (long) Math.floor(serial);
        double frac = serial - days;
        long nanos = Math.round(frac * NANOS_PER_DAY);
        return LocalDateTime.of(EPOCH, LocalTime.MIN).plusDays(days).plusNanos(nanos);
    }

    /** 将 LocalDate 转为 Excel 序列号。 */
    static double toSerial(LocalDate date) {
        return ChronoUnit.DAYS.between(EPOCH, date);
    }

    /** 将 LocalDateTime 转为 Excel 序列号（含时间分量）。 */
    static double toSerial(LocalDateTime dateTime) {
        long days = ChronoUnit.DAYS.between(EPOCH, dateTime.toLocalDate());
        LocalTime t = dateTime.toLocalTime();
        double frac = (t.toSecondOfDay() + t.getNano() / 1_000_000_000.0) / 86400.0;
        return days + frac;
    }

    /**
     * 按格式串与序列号渲染日期/时间为字符串（借鉴 POI DataFormatter，简化版）。
     * 依据格式串占位符判定日期/时间/日期时间，使用 ISO 风格模式渲染；格式串缺失时按是否有小数分量判定。
     * <p>m 在无 h/s 时间上下文时视为月份（归入日期），有 h/s 时为分钟（归入时间）。
     */
    static String formatDateTime(double serial, String excelFmt) {
        LocalDateTime dt = serialToDateTime(serial);
        boolean hasDate = false;
        boolean hasTime = false;
        if (excelFmt != null && !excelFmt.isEmpty()) {
            String s = excelFmt
                    .replaceAll("\"[^\"]*\"", "")
                    .replaceAll("\\\\.", "")
                    .replaceAll("\\[[^]]*]", "");
            boolean hasMonth = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ("yYdD".indexOf(c) >= 0) {
                    hasDate = true;
                } else if ("hHsS".indexOf(c) >= 0) {
                    hasTime = true;
                } else if (c == 'm' || c == 'M') {
                    hasMonth = true;
                }
            }
            if (hasMonth && !hasTime) {
                hasDate = true;
            }
        }
        boolean fractional = serial != Math.floor(serial);
        if (!hasDate && !hasTime) {
            hasDate = true;
            hasTime = fractional;
        }
        String pattern = (hasDate && hasTime) ? "yyyy-MM-dd HH:mm:ss"
                : hasDate ? "yyyy-MM-dd" : "HH:mm:ss";
        try {
            return dt.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
            return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    // ==================== 写入侧：累积样式与生成 styles.xml ====================

    /** 注册日期格式样式，返回 cellXfs 索引；相同格式串复用同一 numFmtId 与样式索引。 */
    int registerDateStyle(String formatCode) {
        return registerXf(resolveNumFmtId(formatCode));
    }

    /** 解析格式串对应的 numFmtId：已注册则复用，否则分配自定义 id（164 起）。 */
    private int resolveNumFmtId(String formatCode) {
        Integer existing = writeNumFmts.get(formatCode);
        if (existing != null) {
            return existing;
        }
        int id = nextCustomFmtId++;
        writeNumFmts.put(formatCode, id);
        return id;
    }

    /** 注册 cellXf（按 numFmtId 去重），返回样式索引。 */
    private int registerXf(int numFmtId) {
        Integer idx = xfIndexCache.get(numFmtId);
        if (idx != null) {
            return idx;
        }
        int newIdx = writeCellXfs.size();
        writeCellXfs.add(numFmtId);
        xfIndexCache.put(numFmtId, newIdx);
        return newIdx;
    }

    /** 生成 styles.xml 文本：含自定义 numFmts（若有）与 cellXfs；未注册自定义样式时仅含默认样式。 */
    String toXml() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (!writeNumFmts.isEmpty()) {
            sb.append("<numFmts count=\"").append(writeNumFmts.size()).append("\">");
            for (Map.Entry<String, Integer> e : writeNumFmts.entrySet()) {
                sb.append("<numFmt numFmtId=\"").append(e.getValue())
                        .append("\" formatCode=\"").append(escapeAttr(e.getKey())).append("\"/>");
            }
            sb.append("</numFmts>");
        }
        sb.append("<cellXfs count=\"").append(writeCellXfs.size()).append("\">");
        for (int i = 0; i < writeCellXfs.size(); i++) {
            int numFmtId = writeCellXfs.get(i);
            sb.append("<xf numFmtId=\"").append(numFmtId)
                    .append("\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"");
            if (numFmtId != 0) {
                sb.append(" applyNumberFormat=\"1\"");
            }
            sb.append("/>");
        }
        sb.append("</cellXfs>");
        sb.append("</styleSheet>");
        return sb.toString();
    }

    /** 转义 XML 属性值：&, <, >, "。 */
    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
