package cn.jiebaba.summer.office;

import java.io.IOException;
import java.io.InputStream;

/**
 * 表格读取抽象：将输入流解析为 {@link TableData}（行×列）。
 * <p>适用于 CSV 与 Excel（xlsx）。
 */
public interface TableReader {

    /** 从输入流读取并解析为表格数据；调用方负责关闭输入流。 */
    TableData read(InputStream in) throws IOException;
}
