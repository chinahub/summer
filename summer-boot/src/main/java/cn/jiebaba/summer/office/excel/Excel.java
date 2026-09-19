package cn.jiebaba.summer.office.excel;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.TableData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 门面：统一 xlsx 读写入口，支持表格模式与流式回调模式。仅支持 XLSX（OOXML）格式，纯 JDK 实现，零第三方依赖。
 *
 * <p><b>表格模式（TableData）</b> -- 纯 JDK 流式，自动校验文件类型：
 * <pre>{@code
 * TableData table = Excel.read(inputStream).sheet(0).doReadSync();
 * byte[] bytes = Excel.write(table).sheet("Sheet1").doWrite();
 * }</pre>
 *
 * <p><b>流式回调模式</b> -- 纯 JDK SAX，逐行回调，适合超大文件：
 * <pre>{@code
 * Excel.streamingRead(inputStream, (rowIndex, cells) -> {
 *     System.out.println("Row " + rowIndex + ": " + cells);
 * });
 * try (XlsxWriter writer = Excel.streamingWrite(outputStream)) {
 *     writer.beginSheet("Sheet1");
 *     writer.writeRow("Name", "Age");
 *     writer.writeRow("Alice", 25);
 * }
 * }</pre>
 *
 * <p><b>文件类型校验</b>：读取时按魔数校验 -- {@code 50 4B}（ZIP/OOXML）为合法 XLSX；
 * {@code D0 CF 11 E0}（OLE2）为过时 XLS，抛出明确错误提示转换；其他格式同样拒绝。
 */
public final class Excel {

    private Excel() {
    }

    // ==================== 文件类型校验 ====================

    /** 将输入流包装为可回退的 PushbackInputStream（8 字节缓冲），用于魔数探测后重放。 */
    static PushbackInputStream sniff(InputStream in) {
        return in instanceof PushbackInputStream p ? p : new PushbackInputStream(in, 8);
    }

    /**
     * 校验输入流为 XLSX（ZIP/OOXML）格式；读取后回退魔数字节，不消耗流数据。
     * 若为 XLS（OLE2/BIFF8）或其他格式，抛出带明确提示的 OfficeException。
     */
    static void requireXlsx(PushbackInputStream pb) throws IOException {
        byte[] header = new byte[4];
        int n = pb.read(header);
        if (n > 0) {
            pb.unread(header, 0, n);
        }
        boolean isZip = n >= 2 && (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B;
        if (isZip) {
            return;
        }
        boolean isOle2 = n >= 4
                && (header[0] & 0xFF) == 0xD0
                && (header[1] & 0xFF) == 0xCF
                && (header[2] & 0xFF) == 0x11
                && (header[3] & 0xFF) == 0xE0;
        if (isOle2) {
            throw new OfficeException("不支持 XLS（.xls）格式，请转换为 XLSX（.xlsx）后再读取");
        }
        throw new OfficeException("非 XLSX 文件，无法解析（期望 ZIP/OOXML 格式的 .xlsx）");
    }

    // ==================== 表格模式（纯 JDK 流式） ====================

    /** 读 xlsx -> TableData（表格模式，纯 JDK SAX，首行作为表头）。 */
    public static TableReadBuilder read(InputStream in) {
        return new TableReadBuilder(in);
    }

    /** 写 xlsx <- TableData（表格模式，纯 JDK ZipOutputStream 流式）。 */
    public static TableWriteBuilder write(TableData table) {
        return new TableWriteBuilder(table);
    }

    // ==================== 流式回调模式（纯 JDK，大文件友好） ====================

    /** 流式读入第一个工作表，逐行回调 handler（纯 JDK SAX，O(1) 内存每行）；自动校验 XLSX 格式。 */
    public static void streamingRead(InputStream in, RowHandler handler) throws IOException {
        streamingRead(in, 0, handler);
    }

    /** 流式读入指定工作表，逐行回调 handler；自动校验 XLSX 格式。 */
    public static void streamingRead(InputStream in, int sheetIndex, RowHandler handler) throws IOException {
        PushbackInputStream pb = sniff(in);
        requireXlsx(pb);
        try (XlsxReader reader = new XlsxReader(pb)) {
            reader.read(sheetIndex, handler);
        }
    }

    /** 流式写出，返回 XlsxWriter 直接写入 OutputStream（纯 JDK，适合超大文件逐行写）。 */
    public static XlsxWriter streamingWrite(OutputStream out) {
        return new XlsxWriter(out);
    }

    // ==================== 共享：读取为 TableData（校验 XLSX 后 SAX 流式） ====================

    /** 读取指定工作表为 TableData（校验 XLSX 格式后 SAX 流式解析，首行按 header 配置作为表头）。 */
    static TableData readTable(InputStream in, int sheetNo, boolean header) throws IOException {
        PushbackInputStream pb = sniff(in);
        requireXlsx(pb);
        List<List<String>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        boolean[] firstRow = {true};
        try (XlsxReader reader = new XlsxReader(pb)) {
            reader.read(sheetNo, (rowIndex, cells) -> {
                if (header && firstRow[0]) {
                    firstRow[0] = false;
                    headers.addAll(cells);
                } else {
                    rows.add(new ArrayList<>(cells));
                }
            });
        }
        return header ? TableData.of(headers, rows) : TableData.withoutHeaders(rows);
    }

    // ==================== 表格读 Builder（纯 JDK） ====================

    /** 表格读取构建器：支持选择工作表与是否含表头。 */
    public static final class TableReadBuilder {

        private final InputStream input;
        private int sheetNo = 0;
        private boolean header = true;

        TableReadBuilder(InputStream input) {
            this.input = input;
        }

        /** 选择工作表索引（从 0 开始），默认 0。 */
        public TableReadBuilder sheet(int sheetNo) {
            this.sheetNo = sheetNo;
            return this;
        }

        /** 标记首行非表头，全部行作为数据。 */
        public TableReadBuilder noHeader() {
            this.header = false;
            return this;
        }

        /** 同步读取并返回 TableData（校验 XLSX 后纯 JDK SAX 流式解析）。 */
        public TableData doReadSync() {
            try {
                return readTable(input, sheetNo, header);
            } catch (IOException e) {
                throw new OfficeException("Excel 解析失败", e);
            }
        }
    }

    // ==================== 表格写 Builder（纯 JDK） ====================

    /** 表格写入构建器：支持自定义工作表名。 */
    public static final class TableWriteBuilder {

        private final TableData table;
        private String sheetName = "Sheet1";

        TableWriteBuilder(TableData table) {
            this.table = table;
        }

        /** 指定工作表名，默认 "Sheet1"。 */
        public TableWriteBuilder sheet(String name) {
            this.sheetName = name;
            return this;
        }

        /** 同步写出并返回 xlsx 字节数组（纯 JDK ZipOutputStream 流式）。 */
        public byte[] doWrite() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (XlsxWriter writer = new XlsxWriter(out)) {
                writer.beginSheet(sheetName);
                if (!table.headers().isEmpty()) {
                    writer.writeRow(table.headers());
                }
                for (List<String> row : table.rows()) {
                    writer.writeRow(row);
                }
                writer.endSheet();
            } catch (IOException e) {
                throw new OfficeException("Excel 生成失败", e);
            }
            return out.toByteArray();
        }
    }
}
