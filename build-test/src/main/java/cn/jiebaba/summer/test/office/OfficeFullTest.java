package cn.jiebaba.summer.test.office;

import cn.jiebaba.summer.office.Office;
import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.OfficeFormat;
import cn.jiebaba.summer.office.OfficeReader;
import cn.jiebaba.summer.office.OfficeWriter;
import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.TableReader;
import cn.jiebaba.summer.office.TableWriter;
import cn.jiebaba.summer.office.csv.CsvReader;
import cn.jiebaba.summer.office.csv.CsvWriter;
import cn.jiebaba.summer.office.docx.DocxReader;
import cn.jiebaba.summer.office.docx.DocxWriter;
import cn.jiebaba.summer.office.docx.Run;
import cn.jiebaba.summer.office.excel.Excel;
import cn.jiebaba.summer.office.excel.XlsxReader;
import cn.jiebaba.summer.office.excel.XlsxWriter;
import cn.jiebaba.summer.office.md.MarkdownReader;
import cn.jiebaba.summer.office.md.MarkdownWriter;
import cn.jiebaba.summer.office.pdf.PdfReader;
import cn.jiebaba.summer.office.pdf.PdfWriter;
import cn.jiebaba.summer.office.xml.XmlReader;
import cn.jiebaba.summer.office.xml.XmlWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * office 模块补充测试（现位于 summer-boot）：覆盖现有冒烟测试未触及的组件与边界场景。
 * <p>覆盖项：CSV 读写往返与转义、XML 读写往返、Markdown 读写往返、TableData 不可变性与计数、
 * OfficeFormat 扩展名推断与异常、Office 门面工厂、各 Reader 读回自产 Writer 输出、
 * Excel 文件类型校验（拒绝 XLS/非 ZIP）、Excel 流式读写 API。
 */
public class OfficeFullTest {

    private static int passed = 0;
    private static int failed = 0;

    /** 补充测试入口：分组执行各组件断言，统计通过/失败数并以退出码反映结果。 */
    public static void main(String[] args) {
        csvRoundtrip();
        csvDelimiterAndBom();
        xmlRoundtrip();
        markdownRoundtrip();
        tableDataSemantics();
        officeFormatInference();
        officeFacade();
        docxReadback();
        pdfReadback();
        excelFileTypeValidation();
        excelStreamingApi();

        System.out.println();
        System.out.println("Office full test: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** CSV 读写往返：含逗号、双引号、换行的字段须正确转义并原样读回。 */
    private static void csvRoundtrip() {
        header("csv roundtrip with escaping");
        TableData src = TableData.of(
                List.of("name", "note"),
                List.of(
                        List.of("Alice", "a,b"),
                        List.of("Bob", "say \"hi\""),
                        List.of("Carol", "line1\nline2")));
        try {
            byte[] csv = new CsvWriter().write(src);
            TableData back = new CsvReader().read(new ByteArrayInputStream(csv));
            expect("row count", 3, back.rowCount());
            expect("header", List.of("name", "note"), back.headers());
            expect("plain field", "Alice", back.rows().get(0).get(0));
            expect("comma field", "a,b", back.rows().get(0).get(1));
            expect("quote field", "say \"hi\"", back.rows().get(1).get(1));
            expect("newline field", "line1\nline2", back.rows().get(2).get(1));
        } catch (Exception e) {
            expect("csv roundtrip no exception", true, false);
        }
    }

    /** CSV 自定义分隔符与 BOM 写出：分号分隔且首三字节为 UTF-8 BOM。 */
    private static void csvDelimiterAndBom() {
        header("csv delimiter and bom");
        try {
            TableData src = TableData.withoutHeaders(
                    List.of(List.of("a", "b", "c")));
            byte[] csv = new CsvWriter(';', true).write(src);
            expect("bom byte0", 0xEF, csv[0] & 0xFF);
            expect("bom byte1", 0xBB, csv[1] & 0xFF);
            expect("bom byte2", 0xBF, csv[2] & 0xFF);
            // CsvReader 不跳 BOM，含 BOM 读回首字段前缀为 ﻿；验证分隔符生效即可
            TableData back = new CsvReader(';', false).read(new ByteArrayInputStream(csv));
            expect("semicolon split count", 3, back.rows().get(0).size());
            expect("semicolon field0 ends with a", true, back.rows().get(0).get(0).endsWith("a"));
            expect("semicolon field1", "b", back.rows().get(0).get(1));
            // 无 BOM 往返首字段干净
            byte[] noBom = new CsvWriter(';', false).write(src);
            TableData clean = new CsvReader(';', false).read(new ByteArrayInputStream(noBom));
            expect("no-bom field0", "a", clean.rows().get(0).get(0));
        } catch (Exception e) {
            expect("csv delimiter no exception", true, false);
        }
    }

    /** XML 读写往返：XmlWriter 包装后 XmlReader 能提取回内容文本。 */
    private static void xmlRoundtrip() {
        header("xml roundtrip");
        try {
            String content = "hello world";
            byte[] xml = new XmlWriter().write(content);
            String raw = new String(xml, StandardCharsets.UTF_8);
            String text = new XmlReader().read(new ByteArrayInputStream(xml));
            expect("xml contains content element", true, raw.contains("<content>hello world</content>"));
            expect("xml document root", true, raw.contains("<document>"));
            expect("xml reader extracts text", true, text.contains("content: hello world"));
        } catch (Exception e) {
            expect("xml roundtrip no exception", true, false);
        }
    }

    /** Markdown 读写往返：原文经 MarkdownReader 读回应与原文一致（关闭尾部换行）。 */
    private static void markdownRoundtrip() {
        header("markdown roundtrip");
        try {
            String md = "# Title\nbody **bold**";
            byte[] bytes = new MarkdownWriter(false).write(md);
            String back = new MarkdownReader().read(new ByteArrayInputStream(bytes));
            expect("markdown identity", md, back);
            // 默认构造追加尾部换行
            byte[] withNl = new MarkdownWriter().write(md);
            expect("markdown trailing newline", true, new String(withNl, StandardCharsets.UTF_8).endsWith("\n"));
        } catch (Exception e) {
            expect("markdown roundtrip no exception", true, false);
        }
    }

    /** TableData 不可变性与计数：构造后修改源列表不影响内部副本，rowCount/columnCount 正确。 */
    private static void tableDataSemantics() {
        header("tabledata semantics");
        List<String> headers = new java.util.ArrayList<>(List.of("k", "v"));
        List<List<String>> rows = new java.util.ArrayList<>(List.of(List.of("a", "1")));
        TableData t = TableData.of(headers, rows);
        headers.add("x");
        rows.add(List.of("b", "2"));
        expect("headers immutable copy", 2, t.headers().size());
        expect("rows immutable copy", 1, t.rowCount());
        expect("column count", 2, t.columnCount());
        TableData empty = TableData.withoutHeaders(List.of());
        expect("empty column count", 0, empty.columnCount());
    }

    /** OfficeFormat.fromExtension：全部支持格式正确推断，空名/无扩展名/未知扩展名抛异常。 */
    private static void officeFormatInference() {
        header("officeformat inference");
        expect("csv", OfficeFormat.CSV, OfficeFormat.fromExtension("a.csv"));
        expect("xlsx", OfficeFormat.XLSX, OfficeFormat.fromExtension("a.XLSX"));
        expect("docx", OfficeFormat.DOCX, OfficeFormat.fromExtension("a.docx"));
        expect("pdf", OfficeFormat.PDF, OfficeFormat.fromExtension("a.pdf"));
        expect("xml", OfficeFormat.XML, OfficeFormat.fromExtension("a.xml"));
        expect("md", OfficeFormat.MD, OfficeFormat.fromExtension("a.md"));
        expect("markdown", OfficeFormat.MD, OfficeFormat.fromExtension("a.markdown"));
        expect("null throws", true, throwsOfficeException(() -> OfficeFormat.fromExtension(null)));
        expect("no ext throws", true, throwsOfficeException(() -> OfficeFormat.fromExtension("noext")));
        expect("unknown throws", true, throwsOfficeException(() -> OfficeFormat.fromExtension("a.xyz")));
    }

    /** Office 门面工厂：create() 返回各 reader/writer 类型正确。 */
    private static void officeFacade() {
        header("office facade factories");
        Office o = Office.create();
        expect("csvReader", true, o.csvReader() instanceof CsvReader);
        expect("csvWriter", true, o.csvWriter() instanceof CsvWriter);
        expect("markdownReader", true, o.markdownReader() instanceof MarkdownReader);
        expect("markdownWriter", true, o.markdownWriter() instanceof MarkdownWriter);
        expect("xmlReader", true, o.xmlReader() instanceof XmlReader);
        expect("xmlWriter", true, o.xmlWriter() instanceof XmlWriter);
        expect("excelReader", true, o.excelReader() instanceof TableReader);
        expect("excelWriter", true, o.excelWriter() instanceof TableWriter);
        expect("docxReader", true, o.docxReader() instanceof DocxReader);
        expect("docxWriter", true, o.docxWriter() instanceof DocxWriter);
        expect("pdfReader", true, o.pdfReader() instanceof PdfReader);
        expect("pdfWriter", true, o.pdfWriter() instanceof PdfWriter);
    }

    /** DOCX 读回：DocxWriter 生成的文档经 DocxReader 提取应包含写入的文本。 */
    private static void docxReadback() {
        header("docx readback");
        try {
            byte[] docx = new DocxWriter()
                    .heading(1, "标题一")
                    .paragraph(Run.text("正文段落"), Run.bold("加粗"))
                    .table(List.of(List.of("姓名", "分数"), List.of("张三", "95")), true)
                    .build();
            String text = new DocxReader().read(new ByteArrayInputStream(docx));
            expect("docx heading readback", true, text.contains("标题一"));
            expect("docx paragraph readback", true, text.contains("正文段落"));
            expect("docx bold readback", true, text.contains("加粗"));
            expect("docx table cell readback", true, text.contains("张三"));
        } catch (Exception e) {
            expect("docx readback no exception", true, false);
        }
    }

    /** PDF 读回：PdfWriter（Helvetica 路径）生成的 PDF 经 PdfReader 提取应包含写入文本。 */
    private static void pdfReadback() {
        header("pdf readback");
        try {
            byte[] pdf = new PdfWriter().write("Hello World\nSecond Line");
            String text = new PdfReader().read(new ByteArrayInputStream(pdf));
            expect("pdf line1 readback", true, text.contains("Hello World"));
            expect("pdf line2 readback", true, text.contains("Second Line"));
        } catch (Exception e) {
            expect("pdf readback no exception", true, false);
        }
    }

    /** Excel 文件类型校验：XLS（OLE2）与非 ZIP 字节均抛 OfficeException，合法 XLSX 正常。 */
    private static void excelFileTypeValidation() {
        header("excel file type validation");
        byte[] xls = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2, 3, 4};
        expect("xls rejected", true, throwsOfficeException(() -> Excel.read(new ByteArrayInputStream(xls)).doReadSync()));
        expect("xls streaming rejected", true, throwsOfficeException(() -> {
            try {
                Excel.streamingRead(new ByteArrayInputStream(xls), (i, c) -> { });
            } catch (java.io.IOException ex) {
                throw new OfficeException("io", ex);
            }
        }));
        byte[] notZip = "not a zip file".getBytes(StandardCharsets.UTF_8);
        expect("non-zip rejected", true, throwsOfficeException(() -> Excel.read(new ByteArrayInputStream(notZip)).doReadSync()));
        // 合法 XLSX：构造最小工作簿并读回
        try {
            TableData src = TableData.of(List.of("k"), List.of(List.of("v")));
            byte[] xlsx = Excel.write(src).sheet("S").doWrite();
            TableData back = Excel.read(new ByteArrayInputStream(xlsx)).doReadSync();
            expect("valid xlsx header", List.of("k"), back.headers());
            expect("valid xlsx row", List.of("v"), back.rows().get(0));
        } catch (Exception e) {
            expect("valid xlsx no exception", true, false);
        }
    }

    /** Excel 流式读写 API：streamingWrite 写出后 streamingRead 读回，含多类型单元格。 */
    private static void excelStreamingApi() {
        header("excel streaming api");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (XlsxWriter w = Excel.streamingWrite(out)) {
                w.beginSheet("Stream")
                        .writeRow("name", "date", "amount")
                        .writeRow("Alice", LocalDate.of(2024, 1, 1), 100.5)
                        .writeRow("Bob", "text")
                        .endSheet();
            }
            byte[] xlsx = out.toByteArray();
            List<List<String>> rows = new java.util.ArrayList<>();
            Excel.streamingRead(new ByteArrayInputStream(xlsx), (idx, cells) -> rows.add(new java.util.ArrayList<>(cells)));
            expect("streaming row count", 3, rows.size());
            expect("streaming header", List.of("name", "date", "amount"), rows.get(0));
            expect("streaming date cell", "2024-01-01", rows.get(1).get(1));
            expect("streaming number cell", "100.5", rows.get(1).get(2));
            expect("streaming ragged row", "Bob", rows.get(2).get(0));
            // XlsxReader 直接使用（Closeable）
            List<List<String>> direct = new java.util.ArrayList<>();
            try (XlsxReader r = new XlsxReader(new ByteArrayInputStream(xlsx))) {
                r.read((idx, cells) -> direct.add(new java.util.ArrayList<>(cells)));
            }
            expect("xlsxreader direct count", 3, direct.size());
        } catch (Exception e) {
            expect("streaming api no exception", true, false);
        }
    }

    /** 执行 Runnable，若抛出 OfficeException 返回 true。 */
    private static boolean throwsOfficeException(Runnable r) {
        try {
            r.run();
            return false;
        } catch (OfficeException e) {
            return true;
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
            failed++;
            System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
