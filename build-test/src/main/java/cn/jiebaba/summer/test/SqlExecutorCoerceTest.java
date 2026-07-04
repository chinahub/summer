package cn.jiebaba.summer.test;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 验证 {@link SqlExecutor#coerce} 新增的 时间/数值 跨类型转换能力
 * （框架缺陷：ORM 此前不做类型转换，导致 TIMESTAMP 列无法映射到 LocalDateTime 字段等）。
 */
public class SqlExecutorCoerceTest {

    private static Object coerce(Object value, Class<?> target) throws Exception {
        Method m = SqlExecutor.class.getDeclaredMethod("coerce", Object.class, Class.class);
        m.setAccessible(true);
        return m.invoke(null, value, target);
    }

    @Test
    void timestampToLocalDateTime() throws Exception {
        Timestamp ts = Timestamp.valueOf("2024-01-02 03:04:05");
        Object r = coerce(ts, LocalDateTime.class);
        Assert.assertTrue(r instanceof LocalDateTime, "expected LocalDateTime, got " + r);
        Assert.assertEquals(LocalDateTime.parse("2024-01-02T03:04:05"), r);
    }

    @Test
    void timestampToInstant() throws Exception {
        Timestamp ts = Timestamp.valueOf("2024-01-02 03:04:05");
        Object r = coerce(ts, Instant.class);
        Assert.assertTrue(r instanceof Instant, "expected Instant, got " + r);
        Assert.assertEquals(ts.toInstant(), r);
    }

    @Test
    void sqlDateToLocalDate() throws Exception {
        Object r = coerce(java.sql.Date.valueOf("2024-01-02"), LocalDate.class);
        Assert.assertTrue(r instanceof LocalDate, "expected LocalDate, got " + r);
        Assert.assertEquals(LocalDate.parse("2024-01-02"), r);
    }

    @Test
    void longNumberToBigDecimal() throws Exception {
        Assert.assertEquals(new BigDecimal("123"), coerce(123L, BigDecimal.class));
    }

    @Test
    void doubleNumberToBigDecimal() throws Exception {
        Assert.assertEquals(new BigDecimal("1.5"), coerce(1.5, BigDecimal.class));
    }

    @Test
    void stringToBigDecimal() throws Exception {
        Assert.assertEquals(new BigDecimal("99.95"), coerce("99.95", BigDecimal.class));
    }

    @Test
    void passthroughWhenAlreadyAssignable() throws Exception {
        Timestamp ts = Timestamp.valueOf("2024-01-02 03:04:05");
        Assert.assertEquals(ts, coerce(ts, Timestamp.class));
    }
}
