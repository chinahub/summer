package cn.jiebaba.summer.core.scheduling;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Locale;

/**
 * Minimal 5-field cron expression: {@code minute hour day-of-month month day-of-week}.
 * Supports star, comma lists, ranges (1-5), step values (star/5, 0-30/10),
 * and names for months/day-of-week (jan..dec, sun..sat). Computes the next fire time
 * after a given moment; {@link ScheduledTaskRegistrar} derives the delay from it.
 */
public final class CronExpression {

    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(8);
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
        // normalize 7 -> 0 (Sunday)
        if (daysOfWeek.get(7)) { daysOfWeek.set(0); daysOfWeek.clear(7); }
    }

    public String expression() { return expression; }

    /** Next fire time at or after the given time (truncated to minutes). */
    public LocalDateTime nextFire(LocalDateTime after) {
        LocalDateTime t = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        for (int i = 0; i < 366 * 24 * 60; i++) {
            if (minutes.get(t.getMinute())
                    && hours.get(t.getHour())
                    && months.get(t.getMonthValue())
                    && daysOfMonth.get(t.getDayOfMonth())
                    && daysOfWeek.get(t.getDayOfWeek().getValue() % 7)) {
                return t;
            }
            t = t.plusMinutes(1);
        }
        throw new IllegalStateException("No next fire time within a year for cron: " + expression);
    }

    private void parse(String field, int min, int max, BitSet target) {
        parse(field, min, max, target, null);
    }

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
