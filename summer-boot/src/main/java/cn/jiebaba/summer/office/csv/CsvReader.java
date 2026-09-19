package cn.jiebaba.summer.office.csv;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.TableReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 读取器：纯 JDK 实现，兼容 RFC 4180。
 * <p>支持双引号包裹的字段（含逗号、换行、引号转义），默认逗号分隔。
 * 首行视为表头可通过 {@link #withHeader} 控制。
 */
public class CsvReader implements TableReader {

    private final char delimiter;
    private final boolean header;

    public CsvReader() {
        this(',', true);
    }

    /** 指定分隔符与是否含表头。 */
    public CsvReader(char delimiter, boolean header) {
        this.delimiter = delimiter;
        this.header = header;
    }

    /** 构造不含表头的读取器。 */
    public static CsvReader withoutHeader() {
        return new CsvReader(',', false);
    }

    /** 从输入流解析 CSV 为表格数据；逐字符状态机解析引号包裹、转义与分隔符，首行按配置决定是否作为表头。 */
    @Override
    public TableData read(InputStream in) throws IOException {
        List<List<String>> allRows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder field = new StringBuilder();
            List<String> row = new ArrayList<>();
            boolean inQuotes = false;
            boolean fieldStarted = false;
            int ch;
            while ((ch = reader.read()) != -1) {
                if (inQuotes) {
                    if (ch == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            inQuotes = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append((char) ch);
                    }
                } else {
                    if (ch == '"') {
                        inQuotes = true;
                        fieldStarted = true;
                    } else if (ch == delimiter) {
                        row.add(field.toString());
                        field.setLength(0);
                        fieldStarted = false;
                    } else if (ch == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                        row.add(field.toString());
                        field.setLength(0);
                        allRows.add(row);
                        row = new ArrayList<>();
                        fieldStarted = false;
                    } else if (ch == '\n') {
                        row.add(field.toString());
                        field.setLength(0);
                        allRows.add(row);
                        row = new ArrayList<>();
                        fieldStarted = false;
                    } else {
                        field.append((char) ch);
                        fieldStarted = true;
                    }
                }
            }
            if (fieldStarted || !row.isEmpty() || field.length() > 0) {
                row.add(field.toString());
                allRows.add(row);
            }
        } catch (IOException e) {
            throw new OfficeException("CSV 解析失败", e);
        }
        if (header && !allRows.isEmpty()) {
            List<String> headers = allRows.remove(0);
            return TableData.of(headers, allRows);
        }
        return TableData.withoutHeaders(allRows);
    }
}
