package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.DateUtil;

import java.util.Date;

public class DateUtilTest {

    @Test
    public void nowAndToday() {
        Assert.assertTrue(DateUtil.now().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        Assert.assertTrue(DateUtil.today().matches("\\d{4}-\\d{2}-\\d{2}"));
        Assert.assertTrue(DateUtil.current() > 0);
        Assert.assertTrue(DateUtil.currentTimeSeconds() > 0);
    }

    @Test
    public void formatAndParse() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assert.assertEquals("2024-06-15 10:20:30", DateUtil.formatDateTime(d));
        Assert.assertEquals("2024-06-15", DateUtil.formatDate(d));
        Assert.assertEquals("10:20:30", DateUtil.formatTime(d));
        Assert.assertEquals("2024/06/15", DateUtil.format(d, "yyyy/MM/dd"));

        Assert.assertEquals("2024-06-15 10:20:30", DateUtil.formatDateTime(DateUtil.parse("2024-06-15 10:20:30")));
        Assert.assertEquals("2024-06-15", DateUtil.formatDate(DateUtil.parse("2024/06/15")));
        Assert.assertEquals("10:20:30", DateUtil.formatTime(DateUtil.parse("10:20:30")));
        Assert.assertEquals("2024-06-15", DateUtil.formatDate(DateUtil.parseDate("2024-06-15")));
        Assert.assertNull(DateUtil.formatDate(null));
    }

    @Test
    public void offset() {
        Date base = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assert.assertEquals("2024-06-16 10:20:30", DateUtil.formatDateTime(DateUtil.offsetDay(base, 1)));
        Assert.assertEquals("2024-06-15 11:20:30", DateUtil.formatDateTime(DateUtil.offsetHour(base, 1)));
        Assert.assertEquals("2024-07-15 10:20:30", DateUtil.formatDateTime(DateUtil.offsetMonth(base, 1)));
        Assert.assertEquals("2025-06-15 10:20:30", DateUtil.formatDateTime(DateUtil.offsetYear(base, 1)));
        Assert.assertEquals("2024-06-22 10:20:30", DateUtil.formatDateTime(DateUtil.offsetWeek(base, 1)));
        Assert.assertEquals("2024-06-16 10:20:30", DateUtil.formatDateTime(DateUtil.addDays(base, 1)));
        Assert.assertEquals("2024-06-14 10:20:30", DateUtil.formatDateTime(DateUtil.offset(base, DateUtil.DateField.DAY_OF_MONTH, -1)));
    }

    @Test
    public void between() {
        Date a = DateUtil.parseDateTime("2024-06-15 00:00:00");
        Date b = DateUtil.parseDateTime("2024-06-18 00:00:00");
        Assert.assertEquals(3L, DateUtil.betweenDay(a, b));
        Assert.assertEquals(72L, DateUtil.betweenHour(a, b));
        Assert.assertEquals(3 * 24 * 60L, DateUtil.betweenMinute(a, b));
        Assert.assertEquals(3 * 24 * 60 * 60L, DateUtil.betweenSecond(a, b));
    }

    @Test
    public void bounds() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assert.assertEquals("2024-06-15 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfDay(d)));
        Assert.assertEquals("2024-06-15 23:59:59", DateUtil.formatDateTime(DateUtil.endOfDay(d)));
        Assert.assertEquals("2024-06-01 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfMonth(d)));
        Assert.assertEquals("2024-06-30 23:59:59", DateUtil.formatDateTime(DateUtil.endOfMonth(d)));
        Assert.assertEquals("2024-01-01 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfYear(d)));
        Assert.assertEquals("2024-12-31 23:59:59", DateUtil.formatDateTime(DateUtil.endOfYear(d)));
        Assert.assertEquals("10:00:00", DateUtil.formatTime(DateUtil.beginOfHour(d)));
        Assert.assertEquals("10:20:00", DateUtil.formatTime(DateUtil.beginOfMinute(d)));
    }

    @Test
    public void weekBounds() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30"); // Saturday
        Assert.assertEquals("2024-06-10 00:00:00", DateUtil.formatDateTime(DateUtil.beginOfWeek(d)));
        Assert.assertEquals("2024-06-16 23:59:59", DateUtil.formatDateTime(DateUtil.endOfWeek(d)));
    }

    @Test
    public void fields() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assert.assertEquals(2024, DateUtil.year(d));
        Assert.assertEquals(6, DateUtil.month(d));
        Assert.assertEquals(15, DateUtil.dayOfMonth(d));
        Assert.assertEquals(10, DateUtil.hour(d));
        Assert.assertEquals(20, DateUtil.minute(d));
        Assert.assertEquals(30, DateUtil.second(d));
        Assert.assertEquals(6, DateUtil.dayOfWeek(d)); // 2024-06-15 is Saturday -> ISO 6
        Assert.assertTrue(DateUtil.weekOfYear(d) >= 1);
        Assert.assertTrue(DateUtil.isWeekend(d));
        Assert.assertFalse(DateUtil.isWeekend(DateUtil.parseDateTime("2024-06-17 10:00:00")));
    }

    @Test
    public void sameAndCompare() {
        Date a = DateUtil.parseDateTime("2024-06-15 08:00:00");
        Date b = DateUtil.parseDateTime("2024-06-15 20:00:00");
        Assert.assertTrue(DateUtil.isSameDay(a, b));
        Assert.assertFalse(DateUtil.isSameInstant(a, b));
        Assert.assertTrue(DateUtil.before(a, b));
        Assert.assertTrue(DateUtil.after(b, a));
        Assert.assertTrue(DateUtil.compare(a, b) < 0);
    }

    @Test
    public void conversions() {
        Date d = DateUtil.parseDateTime("2024-06-15 10:20:30");
        Assert.assertEquals(d, DateUtil.fromLocalDateTime(DateUtil.toLocalDateTime(d)));
        Assert.assertEquals(d.getTime(), DateUtil.toEpochMilli(d));
        Assert.assertNotNull(DateUtil.toInstant(d));
    }
}