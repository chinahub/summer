package cn.jiebaba.summer.office;

import java.io.IOException;

/**
 * 表格生成抽象：将 {@link TableData} 写出为目标格式的字节数组。
 * <p>适用于 CSV 与 Excel（xlsx）。
 */
public interface TableWriter {

    /** 将表格数据生成为目标格式的字节。 */
    byte[] write(TableData table) throws IOException;
}
