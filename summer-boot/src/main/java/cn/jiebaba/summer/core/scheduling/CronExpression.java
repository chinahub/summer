package cn.jiebaba.summer.core.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Locale;

/**
 * 极简 5 字段 cron 表达式：{@code minute hour day-of-month month day-of-week}。
 * 支持 星号、逗号列表、范围（1-5）、步长值（star/5、0-30/10），
 * 以及月份/星期几的名称（jan..dec、sun..sat）。给定时刻之后的下一次触发时间，
 * 通过逐字段跳跃计算，而非逐分钟扫描。
 *
 * <p>day-of-month 与 day-of-week 遵循 Vixie cron 语义：当两个字段都被限定
 * （都不含 {@code *}）时，任一字段匹配即视为该天匹配；否则两个字段都必须匹配。
 */
public final class CronExpression {

    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(8);
    private final boolean domRestricted;
    private final boolean dowRestricted;
    private final String expression;

    public CronExpression(String expression) {
        this.expression = expression == null ? "" : expression.trim();
        String[] f = this.expression.split("\\s+");
        if (f.length != 5) {
            throw new IllegalArgumentException("Cron expression must have 5 fields: " + this.expression);
        }
        parse(f[0], 0, 59, minutes);
        parse(f[1], 0, 23, hours);
        parse(f[2], 1, 31, daysOfMonth);
        parse(f[3], 1, 12, months, java.time.Month.values());
        parse(f[4], 0, 7, daysOfWeek, java.time.DayOfWeek.values());
        if (daysOfWeek.get(7)) { daysOfWeek.set(0); daysOfWeek.clear(7); }
        this.domRestricted = !f[2].contains("*");
        this.dowRestricted = !f[4].contains("*");
    }

    public String expression() { return expression; }

    /** 给定时刻之后的下一次触发时间。 */
    public LocalDateTime nextFire(LocalDateTime after) {
        LocalDateTime t = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        int startYear = t.getYear();
        while (t.getYear() <= startYear + 100) {
            if (!months.get(t.getMonthValue())) {
                int nm = months.nextSetBit(t.getMonthValue() + 1);
                int yr = t.getYear();
                if (nm < 0 || nm > 12) {
                    yr = t.getYear() + 1;
                    nm = months.nextSetBit(1);
                }
                t = LocalDate.of(yr, nm, 1).atStartOfDay();
                continue;
            }
            if (!dayMatches(t)) {
                t = t.toLocalDate().plusDays(1).atStartOfDay();
                continue;
            }
            if (!hours.get(t.getHour())) {
                int nh = hours.nextSetBit(t.getHour() + 1);
                if (nh < 0 || nh > 23) {
                    t = t.toLocalDate().plusDays(1).atStartOfDay();
                } else {
                    t = LocalDateTime.of(t.toLocalDate(), LocalTime.of(nh, 0));
                }
                continue;
            }
            if (!minutes.get(t.getMinute())) {
                int nmin = minutes.nextSetBit(t.getMinute() + 1);
                if (nmin < 0 || nmin > 59) {
                    t = LocalDateTime.of(t.toLocalDate(), LocalTime.of(t.getHour(), 0)).plusHours(1);
                } else {
                    t = t.withMinute(nmin);
                }
                continue;
            }
            return t;
        }
        throw new IllegalStateException("No next fire time within 100 years for cron: " + expression);
    }

    private boolean dayMatches(LocalDateTime t) {
        boolean dom = daysOfMonth.get(t.getDayOfMonth());
        boolean dow = daysOfWeek.get(t.getDayOfWeek().getValue() % 7);
        if (domRestricted && dowRestricted) {
            return dom || dow;
        }
        return dom && dow;
    }

    private void parse(String field, int min, int max, BitSet target) {
        parse(field, min, max, target, null);
    }

    /**
     * 将单个 cron 字段解析为 BitSet：支持逗号、范围、步长与名称（如月份/星期）。
     */
    private void parse(String field, int min, int max, BitSet target, Enum<?>[] names) {
        for (String part : field.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int step = 1;
            int slash = p.indexOf('/');
            if (slash >= 0) {
                step = Integer.parseInt(p.substring(slash + 1));
                p = p.substring(0, slash);
            }
            int lo = min, hi = max;
            if (!p.equals("*")) {
                int dash = p.indexOf('-');
                if (dash >= 0) {
                    lo = valueOf(p.substring(0, dash), names);
                    hi = valueOf(p.substring(dash + 1), names);
                } else {
                    int v = valueOf(p, names);
                    lo = v;
                    hi = slash >= 0 ? max : v;
                }
            }
            for (int v = lo; v <= hi; v += step) {
                if (v >= min && v <= max) target.set(v);
            }
        }
    }

    private int valueOf(String token, Enum<?>[] names) {
        if (names == null) return Integer.parseInt(token);
        for (Enum<?> e : names) {
            if (e.name().toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT), 0)
                    && token.length() >= 3) {
                return e.ordinal() + 1;
            }
        }
        try { return Integer.parseInt(token); } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Unknown cron name: " + token);
        }
    }
}
