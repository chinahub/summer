package cn.jiebaba.summer.office;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格数据：行×列的二维结构，供 CSV 与 Excel 读写共用。
 * <p>headers 为可选表头（无表头时为空列表）；rows 为数据行，每行是字符串列表。
 * 所有列表在构造时做不可变拷贝，保证线程安全。
 */
public record TableData(List<String> headers, List<List<String>> rows) {

    public TableData {
        headers = headers == null ? List.of() : List.copyOf(headers);
        if (rows == null) {
            rows = List.of();
        } else {
            List<List<String>> copy = new ArrayList<>(rows.size());
            for (List<String> row : rows) {
                copy.add(row == null ? List.of() : List.copyOf(row));
            }
            rows = List.copyOf(copy);
        }
    }

    /** 构造带表头的表格。 */
    public static TableData of(List<String> headers, List<List<String>> rows) {
        return new TableData(headers, rows);
    }

    /** 构造无表头的表格。 */
    public static TableData withoutHeaders(List<List<String>> rows) {
        return new TableData(List.of(), rows);
    }

    /** 数据行数（不含表头）。 */
    public int rowCount() {
        return rows.size();
    }

    /** 表头列数；无表头时取首行列数，空表则返回 0。 */
    public int columnCount() {
        if (!headers.isEmpty()) {
            return headers.size();
        }
        return rows.isEmpty() ? 0 : rows.get(0).size();
    }
}
