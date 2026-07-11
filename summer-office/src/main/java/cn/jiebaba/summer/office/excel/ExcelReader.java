package cn.jiebaba.summer.office.excel;

import cn.jiebaba.summer.office.TableData;
import cn.jiebaba.summer.office.TableReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Excel 读取器：基于自研 {@link XlsxReader}（纯 JDK SAX 流式），读取 xlsx 为 {@link TableData}。
 * <p>SAX 逐行回调，内存占用低；默认首行作为表头，调用 {@link #noHeader()} 可禁用。
 * 读取前自动校验文件类型，仅支持 XLSX（OOXML），不支持过时的 XLS（BIFF8）。
 * 不依赖 Apache POI 或 FastExcel。
 */
public class ExcelReader implements TableReader {

    private int sheetNo = 0;
    private boolean header = true;

    /** 指定读取的工作表索引（从 0 开始），默认 0。 */
    public ExcelReader sheet(int sheetNo) {
        this.sheetNo = sheetNo;
        return this;
    }

    /** 标记首行非表头，全部行作为数据。 */
    public ExcelReader noHeader() {
        this.header = false;
        return this;
    }

    /** 从输入流读取 Excel 为表格数据（校验 XLSX 后 SAX 流式逐行回调）。 */
    @Override
    public TableData read(InputStream in) throws IOException {
        return Excel.readTable(in, sheetNo, header);
    }
}
