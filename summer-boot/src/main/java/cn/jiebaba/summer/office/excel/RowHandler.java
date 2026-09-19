package cn.jiebaba.summer.office.excel;

import java.util.List;

/**
 * Excel 行回调处理器：流式读取时每解析完一行即回调此接口。
 * <p>用于 {@link XlsxReader} 的 SAX 事件驱动读取，实现 O(1) 内存的大文件处理。
 */
@FunctionalInterface
public interface RowHandler {

    /**
     * 处理一行数据。
     *
     * @param rowIndex 行号（从 0 开始）
     * @param cells    该行各单元格的字符串值（空单元格为空字符串）
     */
    void handle(int rowIndex, List<String> cells);
}
