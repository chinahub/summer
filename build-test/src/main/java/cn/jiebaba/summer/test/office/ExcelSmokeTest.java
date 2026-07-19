package cn.jiebaba.summer.test.office;

import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.excel.ErrorValue;
import cn.jiebaba.summer.office.excel.Excel;
import cn.jiebaba.summer.office.excel.Formula;
import cn.jiebaba.summer.office.excel.XlsxReader;
import cn.jiebaba.summer.office.excel.XlsxWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Excel 读写冒烟测试：纯 JDK 流式写出含日期/公式/错误/合并/列宽/冻结/筛选/行高的工作簿，
 * 再读回校验单元格值与结构 XML，并验证 Excel 门面 TableData 往返。
 * <p>覆盖借鉴 POI 重写的未实现功能：样式与日期识别、公式单元格、布尔/错误类型、
 * 合并单元格、列宽、冻结窗格、自动筛选、工作表维度、行高。
 */
public class ExcelSmokeTest {

    private static int passed = 0;

    /** 冒烟测试入口：写出多种单元格类型与结构特性，读回断言值与 XML，最后打印通过断言数。 */
    public static void main(String[] args) throws Exception {
        header("write cells + structural features");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XlsxWriter w = new XlsxWriter(out)) {
            w.beginSheet("S1")
                    .setColumnWidth(0, 20)
                    .freezePanes(0, 1)
                    .rowHeight(30)
                    .writeRow("name", "born", "amount")
                    .writeRow("Alice", LocalDate.of(2000, 1, 1), 95.5)
                    .writeRow(true, new ErrorValue("#DIV/0!"), new Formula("SUM(C2:C3)", 190.0))
                    .writeRow(LocalDateTime.of(2000, 1, 1, 13, 30, 0),
                            LocalTime.of(13, 30),
                            new Formula("NOW()"))
                    .mergeCells("A1:C1")
                    .setAutoFilter("A1:C4")
                    .endSheet();
        }
        byte[] xlsx = out.toByteArray();

        header("worksheet structural xml");
        String sheet = zipEntry(xlsx, "xl/worksheets/sheet1.xml");
        expect("dimension", true, sheet.contains("<dimension ref=\"A1\"/>"));
        expect("cols customWidth", true, sheet.contains("<cols>") && sheet.contains("customWidth=\"1\""));
        expect("freeze pane", true, sheet.contains("<pane ") && sheet.contains("state=\"frozen\""));
        expect("autoFilter", true, sheet.contains("<autoFilter ref=\"A1:C4\"/>"));
        expect("mergeCells", true, sheet.contains("<mergeCells") && sheet.contains("<mergeCell ref=\"A1:C1\"/>"));
        expect("formula f tag", true, sheet.contains("<f>SUM(C2:C3)</f>"));
        expect("error t=e", true, sheet.contains("t=\"e\""));
        expect("date style ref", true, sheet.contains("s=\""));
        expect("row height", true, sheet.contains("ht=\"30\"") && sheet.contains("customHeight=\"1\""));

        header("styles xml numFmt");
        String styles = zipEntry(xlsx, "xl/styles.xml");
        expect("numFmt present", true, styles.contains("<numFmt"));
        expect("date format code", true, styles.contains("yyyy-MM-dd"));
        expect("datetime format code", true, styles.contains("yyyy-MM-dd HH:mm:ss"));
        expect("time format code", true, styles.contains("HH:mm:ss"));

        header("read back values");
        List<List<String>> rows = new ArrayList<>();
        try (XlsxReader r = new XlsxReader(new ByteArrayInputStream(xlsx))) {
            r.read((rowIndex, cells) -> rows.add(new ArrayList<>(cells)));
        }
        expect("row count", 4, rows.size());
        expect("header row", List.of("name", "born", "amount"), rows.get(0));
        expect("string cell", "Alice", rows.get(1).get(0));
        expect("date cell", "2000-01-01", rows.get(1).get(1));
        expect("number cell", "95.5", rows.get(1).get(2));
        expect("boolean cell", "TRUE", rows.get(2).get(0));
        expect("error cell", "#DIV/0!", rows.get(2).get(1));
        expect("formula cached value", "190", rows.get(2).get(2));
        expect("datetime cell", "2000-01-01 13:30:00", rows.get(3).get(0));
        expect("time cell", "13:30:00", rows.get(3).get(1));
        expect("formula no cache", "NOW()", rows.get(3).get(2));

        header("excel facade roundtrip");
        TableData table = TableData.of(List.of("k", "v"),
                List.of(List.of("a", "1"), List.of("b", "2")));
        byte[] facade = Excel.write(table).sheet("T").doWrite();
        TableData back = Excel.read(new ByteArrayInputStream(facade)).sheet(0).doReadSync();
        expect("facade headers", List.of("k", "v"), back.headers());
        expect("facade row0", List.of("a", "1"), back.rows().get(0));
        expect("facade row1", List.of("b", "2"), back.rows().get(1));

        header("inline strings + row hidden/outline");
        ByteArrayOutputStream feat = new ByteArrayOutputStream();
        try (XlsxWriter w = new XlsxWriter(feat)) {
            w.beginSheet("S2")
                    .inlineStrings(true)
                    .writeRow("a", "b", "c")
                    .hideRow()
                    .writeRow("hidden", "x")
                    .outlineLevel(2)
                    .writeRow("grouped", "y")
                    .endSheet();
        }
        byte[] featXlsx = feat.toByteArray();
        String featSheet = zipEntry(featXlsx, "xl/worksheets/sheet1.xml");
        expect("inline string cell", true, featSheet.contains("t=\"inlineStr\""));
        expect("inline is-t element", true, featSheet.contains("<is><t xml:space=\"preserve\">"));
        expect("row hidden flag", true, featSheet.contains("hidden=\"1\""));
        expect("outline level 2", true, featSheet.contains("outlineLevel=\"2\""));

        System.out.println();
        System.out.println("Excel smoke test: " + passed + " assertions passed");
    }

    /** 将 xlsx 字节数组写入临时文件后读取指定 ZIP 条目为字符串，便于断言结构 XML。 */
    static String zipEntry(byte[] xlsx, String name) throws IOException {
        Path tmp = Files.createTempFile("excel-smoke-", ".xlsx");
        try {
            Files.write(tmp, xlsx);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                ZipEntry e = zf.getEntry(name);
                if (e == null) {
                    return "";
                }
                try (InputStream in = zf.getInputStream(e)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
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
