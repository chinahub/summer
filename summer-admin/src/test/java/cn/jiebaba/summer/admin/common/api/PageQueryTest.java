package cn.jiebaba.summer.admin.common.api;

import cn.jiebaba.summer.data.page.IPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页查询参数契约测试：pageNum 最小规范为 1，pageSize 默认 10 且限制 1..100，
 * 保证任何非法入参下 offset 永不为负。
 */
class PageQueryTest {

    @Test
    void zeroValuesNormalizeToFirstPageDefaultSize() {
        PageQuery query = new PageQuery();
        query.setPageNum(0);
        query.setPageSize(0);
        IPage<Object> page = query.toPage();
        assertEquals(1, page.current());
        assertEquals(10, page.size());
        assertEquals(0, page.offset());
    }

    @Test
    void negativePageNumAndOversizedPageSizeAreClamped() {
        PageQuery query = new PageQuery();
        query.setPageNum(-5);
        query.setPageSize(10000);
        IPage<Object> page = query.toPage();
        assertEquals(1, page.current());
        assertEquals(100, page.size());
    }

    @Test
    void nullValuesUseDefaults() {
        PageQuery query = new PageQuery();
        query.setPageNum(null);
        query.setPageSize(null);
        IPage<Object> page = query.toPage();
        assertEquals(1, page.current());
        assertEquals(10, page.size());
    }

    @Test
    void legalValuesPassThrough() {
        PageQuery query = new PageQuery();
        query.setPageNum(3);
        query.setPageSize(50);
        IPage<Object> page = query.toPage();
        assertEquals(3, page.current());
        assertEquals(50, page.size());
        assertEquals(100, page.offset());
    }

    @Test
    void offsetIsNeverNegativeForAnyGarbageInput() {
        int[][] cases = {{0, 0}, {-5, 10000}, {-1, -1}, {Integer.MIN_VALUE, Integer.MAX_VALUE},
                {1, 0}, {2, 101}, {5, 100}};
        for (int[] c : cases) {
            PageQuery query = new PageQuery();
            query.setPageNum(c[0]);
            query.setPageSize(c[1]);
            IPage<Object> page = query.toPage();
            assertTrue(page.current() >= 1, "current 必须 >= 1，实际 " + page.current());
            assertTrue(page.size() >= 1 && page.size() <= 100,
                    "size 必须在 1..100，实际 " + page.size());
            assertTrue(page.offset() >= 0, "offset 永不为负，实际 " + page.offset()
                    + "（pageNum=" + c[0] + ", pageSize=" + c[1] + "）");
        }
    }
}
