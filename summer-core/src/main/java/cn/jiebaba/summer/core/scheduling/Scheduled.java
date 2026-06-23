package cn.jiebaba.summer.core.scheduling;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Schedules.class)
public @interface Scheduled {
    /** Fixed delay in milliseconds between end of one run and start of next. */
    long fixedDelay() default -1;
    /** Fixed rate in milliseconds between start of consecutive runs. */
    long fixedRate() default -1;
    /** Initial delay in milliseconds before the first run. */
    long initialDelay() default 0;
    /** Cron expression (5 fields: min hour day-of-month month day-of-week). */
    String cron() default "";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Schedules {
    Scheduled[] value();
}
