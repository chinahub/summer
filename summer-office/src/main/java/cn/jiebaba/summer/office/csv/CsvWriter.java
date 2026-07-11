package cn.jiebaba.summer.office.csv;

import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.TableWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 写入器：纯 JDK 实现，兼容 RFC 4180。
 * <p>字段含分隔符、双引号或换行时自动以双引号包裹并转义内部引号。
 */
public class CsvWriter implements TableWriter {

    private final char delimiter;
    private final boolean bom;

    public CsvWriter() {
        this(',', false);
    }

    /** 指定分隔符与是否写入 UTF-8 BOM（部分 Excel 需要 BOM 才能正确识别中文）。 */
    public CsvWriter(char delimiter, boolean bom) {
        this.delimiter = delimiter;
        this.bom = bom;
    }

    @Override
    public byte[] write(TableData table) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (bom) {
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);
        }
        if (!table.headers().isEmpty()) {
            writeRow(out, table.headers());
        }
        for (List<String> row : table.rows()) {
            writeRow(out, row);
        }
        return out.toByteArray();
    }

    /** 写出一行：逐字段转义后以分隔符拼接，行尾追加 CRLF（RFC 4180 标准）。 */
    private void writeRow(ByteArrayOutputStream out, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                out.write(delimiter);
            }
            out.writeBytes(escape(row.get(i)).getBytes(StandardCharsets.UTF_8));
        }
        out.write('\r');
        out.write('\n');
    }

    /** 字段含分隔符、引号或换行时以双引号包裹并转义内部引号。 */
    private String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needQuote = field.indexOf(delimiter) >= 0
                || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!needQuote) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
