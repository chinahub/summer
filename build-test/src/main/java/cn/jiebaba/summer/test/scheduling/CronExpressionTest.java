package cn.jiebaba.summer.test.scheduling;

import cn.jiebaba.summer.core.scheduling.CronExpression;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.time.LocalDateTime;

public class CronExpressionTest {

    @Test
    void everyMinute() {
        CronExpression expr = new CronExpression("* * * * *");
        LocalDateTime base = LocalDateTime.of(2026, 6, 25, 9, 0, 0);
        Assert.assertEquals(LocalDateTime.of(2026, 6, 25, 9, 1, 0), expr.nextFire(base));
    }

    @Test
    void specificTimeRollsToNextDay() {
        CronExpression expr = new CronExpression("0 9 * * *");
        Assert.assertEquals(LocalDateTime.of(2026, 6, 25, 9, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 8, 0, 0)));
        Assert.assertEquals(LocalDateTime.of(2026, 6, 26, 9, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 9, 0, 0)));
    }

    @Test
    void weekdaySkipsWeekend() {
        CronExpression expr = new CronExpression("0 9 * * 1-5");
        Assert.assertEquals(LocalDateTime.of(2026, 6, 25, 9, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 8, 0, 0)));
        Assert.assertEquals(LocalDateTime.of(2026, 6, 29, 9, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 26, 9, 0, 0)));
    }

    @Test
    void dayOfMonthOrDayOfWeekWhenBothRestricted() {
        CronExpression expr = new CronExpression("0 0 13 * 5");
        Assert.assertEquals(LocalDateTime.of(2026, 6, 26, 0, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 0, 0, 0)));
    }

    @Test
    void stepMinutes() {
        CronExpression expr = new CronExpression("*/15 * * * *");
        Assert.assertEquals(LocalDateTime.of(2026, 6, 25, 9, 15, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 9, 7, 0)));
    }

    @Test
    void monthNameAndDay() {
        CronExpression expr = new CronExpression("0 0 1 feb *");
        Assert.assertEquals(LocalDateTime.of(2027, 2, 1, 0, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 0, 0, 0)));
    }

    @Test
    void feb29WithinLeapYearCycle() {
        CronExpression expr = new CronExpression("0 0 29 2 *");
        Assert.assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0, 0),
                expr.nextFire(LocalDateTime.of(2026, 6, 25, 0, 0, 0)));
    }

    @Test(expected = IllegalArgumentException.class)
    void wrongFieldCountRejected() {
        new CronExpression("0 */5 * * * *");
    }

    @Test
    void impossibleExpressionHasNoFireTime() {
        CronExpression expr = new CronExpression("0 0 31 2 *");
        Assert.assertThrows(IllegalStateException.class,
                () -> expr.nextFire(LocalDateTime.now()));
    }
}
