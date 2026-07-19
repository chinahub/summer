package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.util.DateUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class DateUtilTest {

    @Test
    public void nowAndToday() {
        Assertions.assertTrue(DateUtil.now().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        Assertions.assertTrue(DateUtil.today().matches("\\d{4}-\\d{2}-\\d{2}"));
        Assertions.assertTrue(DateUtil.current() > 0);
        Assertions.assertTrue(DateUtil.currentTimeSeconds() > 0);
    }

    @Test
    public void formatAndParse() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assertions.assertEquals("2024-06-15 10:20:30", DateUtil.formatDateTime(d));
        Assertions.assertEquals("2024-06-15", DateUtil.formatDate(d));
        Assertions.assertEquals("10:20:30", DateUtil.formatTime(d));
        Assertions.assertEquals("2024/06/15", DateUtil.format(d, "yyyy/MM/dd"));

        Assertions.assertEquals("2024-06-15 10:20:30", DateUtil.formatDateTime(DateUtil.parse("2024-06-15 10:20:30")));
        Assertions.assertEquals("2024-06-15", DateUtil.formatDate(DateUtil.parse("2024/06/15")));
        Assertions.assertEquals("10:20:30", DateUtil.formatTime(DateUtil.parse("10:20:30")));
        Assertions.assertEquals("2024-06-15", DateUtil.formatDate(DateUtil.parseDate("2024-06-15")));
        Assertions.assertNull(DateUtil.formatDate(null));
    }

    @Test
    public void offset() {
        Date base = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assertions.assertEquals("2024-06-16 10:20:30", DateUtil.formatDateTime(DateUtil.offsetDay(base, 1)));
        Assertions.assertEquals("2024-06-15 11:20:30", DateUtil.formatDateTime(DateUtil.offsetHour(base, 1)));
        Assertions.assertEquals("2024-07-15 10:20:30", DateUtil.formatDateTime(DateUtil.offsetMonth(base, 1)));
        Assertions.assertEquals("2025-06-15 10:20:30", DateUtil.formatDateTime(DateUtil.offsetYear(base, 1)));
        Assertions.assertEquals("2024-06-22 10:20:30", DateUtil.formatDateTime(DateUtil.offsetWeek(base, 1)));
        Assertions.assertEquals("2024-06-16 10:20:30", DateUtil.formatDateTime(DateUtil.addDays(base, 1)));
        Assertions.assertEquals("2024-06-14 10:20:30", DateUtil.formatDateTime(DateUtil.offset(base, DateUtil.DateField.DAY_OF_MONTH, -1)));
    }

    @Test
    public void between() {
        Date a = DateUtil.parseDateTime("2024-06-15 00:00:00");
        Date b = DateUtil.parseDateTime("2024-06-18 00:00:00");
        Assertions.assertEquals(3L, DateUtil.betweenDay(a, b));
        Assertions.assertEquals(72L, DateUtil.betweenHour(a, b));
        Assertions.assertEquals(3 * 24 * 60L, DateUtil.betweenMinute(a, b));
        Assertions.assertEquals(3 * 24 * 60 * 60L, DateUtil.betweenSecond(a, b));
    }

    @Test
    public void bounds() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assertions.assertEquals("2024-06-15 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfDay(d)));
        Assertions.assertEquals("2024-06-15 23:59:59", DateUtil.formatDateTime(DateUtil.endOfDay(d)));
        Assertions.assertEquals("2024-06-01 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfMonth(d)));
        Assertions.assertEquals("2024-06-30 23:59:59", DateUtil.formatDateTime(DateUtil.endOfMonth(d)));
        Assertions.assertEquals("2024-01-01 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfYear(d)));
        Assertions.assertEquals("2024-12-31 23:59:59", DateUtil.formatDateTime(DateUtil.endOfYear(d)));
        Assertions.assertEquals("10:00:00", DateUtil.formatTime(DateUtil.beginOfHour(d)));
        Assertions.assertEquals("10:20:00", DateUtil.formatTime(DateUtil.beginOfMinute(d)));
    }

    @Test
    public void weekBounds() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30"); // 星期六
        Assertions.assertEquals("2024-06-10 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfWeek(d)));
        Assertions.assertEquals("2024-06-16 23:59:59", DateUtil.formatDateTime(DateUtil.endOfWeek(d)));
    }

    @Test
    public void fields() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assertions.assertEquals(2024, DateUtil.year(d));
        Assertions.assertEquals(6, DateUtil.month(d));
        Assertions.assertEquals(15, DateUtil.dayOfMonth(d));
        Assertions.assertEquals(10, DateUtil.hour(d));
        Assertions.assertEquals(20, DateUtil.minute(d));
        Assertions.assertEquals(30, DateUtil.second(d));
        Assertions.assertEquals(6, DateUtil.dayOfWeek(d)); // 2024-06-15 为星期六 -> ISO 6
        Assertions.assertTrue(DateUtil.weekOfYear(d) >= 1);
        Assertions.assertTrue(DateUtil.isWeekend(d));
        Assertions.assertFalse(DateUtil.isWeekend(DateUtil.parseDateTime("2024-06-17 10:00:00")));
    }

    @Test
    public void sameAndCompare() {
        Date a = DateUtil.parseDateTime("2024-06-15 08:00:00");
        Date b = DateUtil.parseDateTime("2024-06-15 20:00:00");
        Assertions.assertTrue(DateUtil.isSameDay(a, b));
        Assertions.assertFalse(DateUtil.isSameInstant(a, b));
        Assertions.assertTrue(DateUtil.before(a, b));
        Assertions.assertTrue(DateUtil.after(b, a));
        Assertions.assertTrue(DateUtil.compare(a, b) < 0);
    }

    @Test
    public void conversions() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assertions.assertEquals(d, DateUtil.fromLocalDateTime(DateUtil.toLocalDateTime(d)));
        Assertions.assertEquals(d.getTime(), DateUtil.toEpochMilli(d));
        Assertions.assertNotNull(DateUtil.toInstant(d));
    }
}
