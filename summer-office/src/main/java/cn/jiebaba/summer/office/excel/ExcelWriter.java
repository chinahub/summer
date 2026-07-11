package cn.jiebaba.summer.office.excel;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.TableWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Excel 写入器：基于自研 {@link XlsxWriter}（纯 JDK ZipOutputStream 流式），将 {@link TableData} 写为 xlsx 字节数组。
 * <p>逐行写入 ZIP 条目流，内存占用 O(1) 每行；不依赖 Apache POI 或 FastExcel。
 * 默认工作表名为 "Sheet1"；调用 {@link #sheet(String)} 可自定义。
 */
public class ExcelWriter implements TableWriter {

    private String sheetName = "Sheet1";

    /** 指定工作表名，默认 "Sheet1"。 */
    public ExcelWriter sheet(String name) {
        this.sheetName = name;
        return this;
    }

    /** 将表格数据写为 xlsx 字节数组（纯 JDK ZipOutputStream 流式）。 */
    @Override
    public byte[] write(TableData table) throws IOException {
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
        } catch (OfficeException e) {
            throw e;
        }
        return out.toByteArray();
    }
}
