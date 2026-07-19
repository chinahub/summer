package cn.jiebaba.summer.sample.office;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.excel.Excel;
import cn.jiebaba.summer.office.excel.XlsxWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 测试（FastExcel 使用方式）：以“写出 -> 读回”回路验证 summer-office 的 Excel 门面。
 * 覆盖 FastExcel 典型场景：表头 + 数据行的 doWrite/doReadSync、无表头读取、按 sheet 索引读取、
 * ReadListener 式逐行流式读、大数据量流式写，以及非法格式的快速失败。
 */
public class ExcelFastExcelStyleTest {


    /** FastExcel 经典用法镜像：Excel.write(table).sheet(...).doWrite() 后 read(...).sheet(0).doReadSync() 逐行比对。 */
    @Test
    public void writeThenDoReadSyncRoundTrip() {
        TableData table = TableData.of(
                List.of("姓名", "年龄", "城市"),
                List.of(List.of("张三", "25", "北京"),
                        List.of("李四", "30", "上海"),
                        List.of("王五", "28", "广州")));
        byte[] bytes = Excel.write(table).sheet("用户表").doWrite();

        // xlsx 为 ZIP 容器，首两字节魔数应为 PK
        Assertions.assertEquals(0x50, bytes[0] & 0xFF);
        Assertions.assertEquals(0x4B, bytes[1] & 0xFF);

        TableData back = Excel.read(new ByteArrayInputStream(bytes)).sheet(0).doReadSync();
        Assertions.assertEquals(List.of("姓名", "年龄", "城市"), back.headers());
        Assertions.assertEquals(3, back.rowCount());
        Assertions.assertEquals(3, back.columnCount());
        Assertions.assertIterableEquals(List.of("张三", "25", "北京"), back.rows().get(0));
        Assertions.assertIterableEquals(List.of("李四", "30", "上海"), back.rows().get(1));
        Assertions.assertIterableEquals(List.of("王五", "28", "广州"), back.rows().get(2));
    }

    /** FastExcel 无头场景：noHeader() 时首行不作为表头，全部行进入数据区。 */
    @Test
    public void readWithoutHeader() {
        TableData table = TableData.withoutHeaders(
                List.of(List.of("a", "1"), List.of("b", "2")));
        byte[] bytes = Excel.write(table).sheet("S").doWrite();

        TableData back = Excel.read(new ByteArrayInputStream(bytes)).sheet(0).noHeader().doReadSync();
        Assertions.assertTrue(back.headers().isEmpty(), "noHeader 模式不应解析表头");
        Assertions.assertEquals(2, back.rowCount());
        Assertions.assertIterableEquals(List.of("a", "1"), back.rows().get(0));
    }

    /** FastExcel sheetNo 场景：一个工作簿两个工作表，按索引精确读取目标 sheet。 */
    @Test
    public void selectSheetByIndex() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XlsxWriter w = Excel.streamingWrite(out)) {
            w.beginSheet("第一").writeRow("a1", "a2").endSheet();
            w.beginSheet("第二").writeRow("b1", "b2").writeRow("b3", "b4").endSheet();
        }
        byte[] bytes = out.toByteArray();

        TableData first = Excel.read(new ByteArrayInputStream(bytes)).sheet(0).noHeader().doReadSync();
        Assertions.assertEquals(1, first.rowCount());
        Assertions.assertIterableEquals(List.of("a1", "a2"), first.rows().get(0));

        TableData second = Excel.read(new ByteArrayInputStream(bytes)).sheet(1).noHeader().doReadSync();
        Assertions.assertEquals(2, second.rowCount());
        Assertions.assertIterableEquals(List.of("b1", "b2"), second.rows().get(0));
        Assertions.assertIterableEquals(List.of("b3", "b4"), second.rows().get(1));
    }

    /** FastExcel ReadListener 场景：streamingRead 逐行回调，行序与内容须与写入一致。 */
    @Test
    public void streamingReadLikeListener() throws Exception {
        TableData table = TableData.of(List.of("k", "v"),
                List.of(List.of("x", "10"), List.of("y", "20")));
        byte[] bytes = Excel.write(table).sheet("S").doWrite();

        List<List<String>> received = new ArrayList<>();
        List<Integer> rowIndexes = new ArrayList<>();
        Excel.streamingRead(new ByteArrayInputStream(bytes), (rowIndex, cells) -> {
            rowIndexes.add(rowIndex);
            received.add(new ArrayList<>(cells));
        });
        Assertions.assertEquals(List.of(0, 1, 2), rowIndexes);
        Assertions.assertIterableEquals(List.of("k", "v"), received.get(0));
        Assertions.assertIterableEquals(List.of("x", "10"), received.get(1));
        Assertions.assertIterableEquals(List.of("y", "20"), received.get(2));
    }

    /** FastExcel 大数据量场景：流式写出一千行，流式读回计数一致且抽样内容正确。 */
    @Test
    public void streamingWriteLargeSheet() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XlsxWriter w = Excel.streamingWrite(out)) {
            w.beginSheet("大数据").writeRow("编号", "值");
            for (int i = 0; i < 1000; i++) {
                w.writeRow("row-" + i, String.valueOf(i));
            }
            w.endSheet();
        }

        int[] count = {0};
        String[] lastFirstCell = {null};
        Excel.streamingRead(new ByteArrayInputStream(out.toByteArray()), (rowIndex, cells) -> {
            count[0]++;
            lastFirstCell[0] = cells.get(0);
        });
        Assertions.assertEquals(1001, count[0]);
        Assertions.assertEquals("row-999", lastFirstCell[0]);
    }

    /** 快速失败：XLS（OLE2/BIFF8）魔数应被识别并给出明确错误，而不是解析出乱码。 */
    @Test
    public void rejectLegacyXls() {
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x01, 0x02, 0x03, 0x04};
        OfficeException e = Assertions.assertThrows(OfficeException.class,
                () -> Excel.read(new ByteArrayInputStream(ole2)).sheet(0).doReadSync());
        Assertions.assertTrue(e.getMessage() != null && e.getMessage().contains("XLS"),
                "错误信息应提示转换为 XLSX");
    }

    /** 快速失败：纯文本等非法内容应抛出 OfficeException。 */
    @Test
    public void rejectNonExcelContent() {
        byte[] text = "this is not an excel file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertThrows(OfficeException.class,
                () -> Excel.read(new ByteArrayInputStream(text)).sheet(0).doReadSync());
    }
}
