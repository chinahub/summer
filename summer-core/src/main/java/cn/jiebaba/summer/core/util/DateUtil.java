package cn.jiebaba.summer.core.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.Locale;

/**
 * 日期时间工具，灵感来自 {@code cn.hutool.core.date.DateUtil}。
 *
 * <p>完全基于 {@code java.time} 实现，并保留 {@link java.util.Date} 外观以兼容遗留 API。
 * Date↔LocalDateTime 转换使用 {@link ZoneId#systemDefault()}。
 */
public final class DateUtil {

    private DateUtil() {}

    public static final String NORM_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String NORM_DATE_PATTERN = "yyyy-MM-dd";
    public static final String NORM_TIME_PATTERN = "HH:mm:ss";
    public static final String UTC_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String UTC_MS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String[] PARSE_PATTERNS = {
            NORM_DATETIME_PATTERN, "yyyy-MM-dd HH:mm", NORM_DATE_PATTERN,
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM/dd",
            "yyyyMMddHHmmss", "yyyyMMdd", NORM_TIME_PATTERN, "HH:mm"
    };

    /** 当前时间，格式 {@code yyyy-MM-dd HH:mm:ss}。 */
    public static String now() { return formatDateTime(new Date()); }

    /** 当前日期，格式 {@code yyyy-MM-dd}。 */
    public static String today() { return formatDate(new Date()); }

    /** 当前 epoch 毫秒。 */
    public static long current() { return System.currentTimeMillis(); }

    /** 当前 epoch 秒。 */
    public static long currentTimeSeconds() { return System.currentTimeMillis() / 1000L; }

    public static Date date() { return new Date(); }
    public static Date date(long epochMilli) { return new Date(epochMilli); }
    public static Date date(Instant instant) { return instant == null ? null : Date.from(instant); }

    // ---- 格式化 --------------------------------------------------------------

    public static String format(Date date, String pattern) {
        if (date == null) return null;
        return DateTimeFormatter.ofPattern(pattern == null ? NORM_DATETIME_PATTERN : pattern, Locale.getDefault())
                .format(toLocalDateTime(date));
    }
    public static String format(LocalDateTime time, String pattern) {
        if (time == null) return null;
        return DateTimeFormatter.ofPattern(pattern == null ? NORM_DATETIME_PATTERN : pattern, Locale.getDefault()).format(time);
    }
    public static String formatDate(Date date) { return format(date, NORM_DATE_PATTERN); }
    public static String formatDateTime(Date date) { return format(date, NORM_DATETIME_PATTERN); }
    public static String formatTime(Date date) { return format(date, NORM_TIME_PATTERN); }

    // ---- 解析 ---------------------------------------------------------------

    public static Date parse(String dateStr) {
        if (dateStr == null) return null;
        String s = dateStr.trim();
        for (String p : PARSE_PATTERNS) {
            Date d = tryParse(s, p);
            if (d != null) return d;
        }
        throw new IllegalArgumentException("Cannot parse date string: " + dateStr);
    }

    public static Date parse(String dateStr, String pattern) {
        if (dateStr == null) return null;
        return tryParse(dateStr.trim(), pattern == null ? NORM_DATETIME_PATTERN : pattern);
    }

    public static Date parseDate(String dateStr) { return parse(dateStr, NORM_DATE_PATTERN); }
    public static Date parseDateTime(String dateStr) { return parse(dateStr, NORM_DATETIME_PATTERN); }
    public static Date parseTime(String dateStr) { return parse(dateStr, NORM_TIME_PATTERN); }
    public static Date parseUTC(String dateStr) {
        String p = (dateStr != null && dateStr.length() >= 23 && dateStr.indexOf('.') >= 0) ? UTC_MS_PATTERN : UTC_PATTERN;
        return parse(dateStr, p);
    }

    private static Date tryParse(String text, String pattern) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern, Locale.getDefault());
            if (pattern.contains("H") || pattern.contains("m") || pattern.contains("s")) {
                if (pattern.contains("y") || pattern.contains("M") || pattern.contains("d")) {
                    return fromLocalDateTime(LocalDateTime.parse(text, fmt));
                }
                return fromLocalTime(LocalTime.parse(text, fmt));
            }
            return fromDate(LocalDate.parse(text, fmt));
        } catch (Exception ignore) {
            return null;
        }
    }

    // ---- 转换 ---------------------------------------------------------

    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZONE).toLocalDateTime();
    }
    public static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZONE).toLocalDate();
    }
    public static LocalTime toLocalTime(Date date) {
        return date == null ? null : date.toInstant().atZone(ZONE).toLocalTime();
    }
    public static Date fromLocalDateTime(LocalDateTime ldt) {
        return ldt == null ? null : Date.from(ldt.atZone(ZONE).toInstant());
    }
    public static Date fromDate(LocalDate ld) {
        return ld == null ? null : Date.from(ld.atStartOfDay(ZONE).toInstant());
    }
    public static Date fromLocalTime(LocalTime lt) {
        return lt == null ? null : Date.from(lt.atDate(LocalDate.now()).atZone(ZONE).toInstant());
    }
    public static Instant toInstant(Date date) { return date == null ? null : date.toInstant(); }
    public static long toEpochMilli(Date date) { return date == null ? 0L : date.getTime(); }
    // ---- 偏移 --------------------------------------------------------------

    public static Date offset(Date date, DateField field, int amount) {
        if (date == null || field == null) return date;
        LocalDateTime ldt = toLocalDateTime(date).plus(amount, field.chrono);
        return fromLocalDateTime(ldt);
    }
    public static Date offsetDay(Date date, int days) { return offset(date, DateField.DAY_OF_MONTH, days); }
    public static Date offsetHour(Date date, int hours) { return offset(date, DateField.HOUR, hours); }
    public static Date offsetMinute(Date date, int minutes) { return offset(date, DateField.MINUTE, minutes); }
    public static Date offsetSecond(Date date, int seconds) { return offset(date, DateField.SECOND, seconds); }
    public static Date offsetMonth(Date date, int months) { return offset(date, DateField.MONTH, months); }
    public static Date offsetYear(Date date, int years) { return offset(date, DateField.YEAR, years); }
    public static Date offsetWeek(Date date, int weeks) { return offset(date, DateField.WEEK, weeks); }

    public static Date addDays(Date date, int days) { return offsetDay(date, days); }
    public static Date addHours(Date date, int hours) { return offsetHour(date, hours); }
    public static Date addMinutes(Date date, int minutes) { return offsetMinute(date, minutes); }
    public static Date addMonths(Date date, int months) { return offsetMonth(date, months); }
    public static Date addYears(Date date, int years) { return offsetYear(date, years); }
    public static Date addWeeks(Date date, int weeks) { return offsetWeek(date, weeks); }

    // ---- 区间 -------------------------------------------------------------

    public static long between(Date begin, Date end, DateUnit unit) {
        if (begin == null || end == null || unit == null) return 0L;
        long diff = Math.abs(end.getTime() - begin.getTime());
        return diff / unit.millis();
    }
    public static long betweenMs(Date begin, Date end) { return between(begin, end, DateUnit.MS); }
    public static long betweenSecond(Date begin, Date end) { return between(begin, end, DateUnit.SECOND); }
    public static long betweenMinute(Date begin, Date end) { return between(begin, end, DateUnit.MINUTE); }
    public static long betweenHour(Date begin, Date end) { return between(begin, end, DateUnit.HOUR); }
    public static long betweenDay(Date begin, Date end) { return between(begin, end, DateUnit.DAY); }

    // ---- 边界 --------------------------------------------------------------

    public static Date beginOfDay(Date date) {
        return date == null ? null : fromLocalDateTime(toLocalDateTime(date).toLocalDate().atStartOfDay());
    }
    public static Date endOfDay(Date date) {
        return date == null ? null : fromLocalDateTime(toLocalDateTime(date).toLocalDate().atTime(23, 59, 59, 0));
    }
    public static Date beginOfHour(Date date) {
        if (date == null) return null;
        LocalDateTime ldt = toLocalDateTime(date);
        return fromLocalDateTime(ldt.toLocalDate().atTime(ldt.getHour(), 0, 0, 0));
    }
    public static Date endOfHour(Date date) {
        if (date == null) return null;
        LocalDateTime ldt = toLocalDateTime(date);
        return fromLocalDateTime(ldt.toLocalDate().atTime(ldt.getHour(), 59, 59, 0));
    }
    public static Date beginOfMinute(Date date) {
        if (date == null) return null;
        LocalDateTime ldt = toLocalDateTime(date);
        return fromLocalDateTime(ldt.toLocalDate().atTime(ldt.getHour(), ldt.getMinute(), 0, 0));
    }
    public static Date endOfMinute(Date date) {
        if (date == null) return null;
        LocalDateTime ldt = toLocalDateTime(date);
        return fromLocalDateTime(ldt.toLocalDate().atTime(ldt.getHour(), ldt.getMinute(), 59, 0));
    }
    public static Date beginOfWeek(Date date) {
        if (date == null) return null;
        LocalDate monday = toLocalDate(date).with(DayOfWeek.MONDAY);
        return fromLocalDateTime(monday.atStartOfDay());
    }
    public static Date endOfWeek(Date date) {
        if (date == null) return null;
        LocalDate sunday = toLocalDate(date).with(DayOfWeek.SUNDAY);
        return fromLocalDateTime(sunday.atTime(23, 59, 59, 0));
    }
    public static Date beginOfMonth(Date date) {
        return date == null ? null : fromLocalDateTime(toLocalDate(date).withDayOfMonth(1).atStartOfDay());
    }
    public static Date endOfMonth(Date date) {
        if (date == null) return null;
        LocalDate first = toLocalDate(date).withDayOfMonth(1);
        return fromLocalDateTime(first.plusMonths(1).minusDays(1).atTime(23, 59, 59, 0));
    }
    public static Date beginOfYear(Date date) {
        return date == null ? null : fromLocalDateTime(toLocalDate(date).withDayOfYear(1).atStartOfDay());
    }
    public static Date endOfYear(Date date) {
        if (date == null) return null;
        int year = toLocalDate(date).getYear();
        return fromLocalDateTime(LocalDate.of(year, 12, 31).atTime(23, 59, 59, 0));
    }

    // ---- 字段访问 -----------------------------------------------------

    public static int year(Date date) { return date == null ? 0 : toLocalDateTime(date).getYear(); }
    public static int month(Date date) { return date == null ? 0 : toLocalDateTime(date).getMonthValue(); }
    public static int dayOfMonth(Date date) { return date == null ? 0 : toLocalDateTime(date).getDayOfMonth(); }
    public static int dayOfYear(Date date) { return date == null ? 0 : toLocalDateTime(date).getDayOfYear(); }
    public static int dayOfWeek(Date date) { return date == null ? 0 : toLocalDateTime(date).getDayOfWeek().getValue(); }
    public static int hour(Date date) { return date == null ? 0 : toLocalDateTime(date).getHour(); }
    public static int minute(Date date) { return date == null ? 0 : toLocalDateTime(date).getMinute(); }
    public static int second(Date date) { return date == null ? 0 : toLocalDateTime(date).getSecond(); }
    public static int weekOfYear(Date date) {
        return date == null ? 0 : toLocalDate(date).get(WeekFields.ISO.weekOfYear());
    }
    public static int weekOfMonth(Date date) {
        return date == null ? 0 : toLocalDate(date).get(WeekFields.ISO.weekOfMonth());
    }

    // ---- 比较 / 谓词 ------------------------------------------------

    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;
        return toLocalDate(date1).equals(toLocalDate(date2));
    }
    public static boolean isSameInstant(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;
        return date1.getTime() == date2.getTime();
    }
    public static boolean isWeekend(Date date) {
        if (date == null) return false;
        DayOfWeek dow = toLocalDate(date).getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
    public static boolean before(Date date, Date when) {
        if (date == null || when == null) return false;
        return date.before(when);
    }
    public static boolean after(Date date, Date when) {
        if (date == null || when == null) return false;
        return date.after(when);
    }
    public static int compare(Date date1, Date date2) {
        if (date1 == null && date2 == null) return 0;
        if (date1 == null) return -1;
        if (date2 == null) return 1;
        return date1.compareTo(date2);
    }

    /** {@link #offset(Date, DateField, int)} 使用的日历字段枚举。 */
    public enum DateField {
        YEAR(ChronoUnit.YEARS),
        MONTH(ChronoUnit.MONTHS),
        DAY_OF_MONTH(ChronoUnit.DAYS),
        DAY_OF_YEAR(ChronoUnit.DAYS),
        HOUR(ChronoUnit.HOURS),
        MINUTE(ChronoUnit.MINUTES),
        SECOND(ChronoUnit.SECONDS),
        WEEK(ChronoUnit.WEEKS),
        DAY_OF_WEEK(ChronoUnit.DAYS);

        private final ChronoUnit chrono;
        DateField(ChronoUnit chrono) { this.chrono = chrono; }
        public ChronoUnit chrono() { return chrono; }
    }

    /** {@link #between(Date, Date, DateUnit)} 使用的时间单位枚举。 */
    public enum DateUnit {
        MS(1L),
        SECOND(1000L),
        MINUTE(60_000L),
        HOUR(3_600_000L),
        DAY(86_400_000L);

        private final long millis;
        DateUnit(long millis) { this.millis = millis; }
        public long millis() { return millis; }
    }

    @SuppressWarnings("unused")
    private static ZonedDateTime zoned(Date date) { return date.toInstant().atZone(ZONE); }
}
