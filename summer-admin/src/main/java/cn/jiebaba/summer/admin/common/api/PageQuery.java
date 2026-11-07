package cn.jiebaba.summer.admin.common.api;

import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.data.page.Page;

/**
 * 分页查询参数：pageNum 从 1 开始，最小规范为 1；pageSize 默认 10，
 * 限制在 1..100 之间，保证任何入参下 offset 永不为负。
 */
public class PageQuery {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private Integer pageNum = DEFAULT_PAGE_NUM;
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 转换为框架分页对象：pageNum 最小规范为 1，pageSize 默认 10 且限制 1..100。
     *
     * @param <T> 行数据类型
     * @return 规范化后的分页对象
     */
    public <T> IPage<T> toPage() {
        int current = pageNum == null ? DEFAULT_PAGE_NUM : Math.max(pageNum, DEFAULT_PAGE_NUM);
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (size < 1) {
            size = DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        return new Page<>(current, size);
    }
}
