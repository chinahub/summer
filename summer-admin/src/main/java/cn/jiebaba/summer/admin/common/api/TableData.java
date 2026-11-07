package cn.jiebaba.summer.admin.common.api;

import cn.jiebaba.summer.data.page.IPage;

import java.util.List;

/**
 * 分页表格响应体：{@code {code, msg, rows, total}}，兼容常见 admin 前端表格组件。
 *
 * @param <T> 行数据类型
 */
public class TableData<T> {

    private final int code;
    private final String msg;
    private final List<T> rows;
    private final long total;

    private TableData(List<T> rows, long total) {
        this.code = R.CODE_SUCCESS;
        this.msg = "查询成功";
        this.rows = rows;
        this.total = total;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public List<T> getRows() {
        return rows;
    }

    public long getTotal() {
        return total;
    }

    public static <T> TableData<T> of(IPage<T> page) {
        return new TableData<>(page.records(), page.total());
    }
}
