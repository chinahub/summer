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
    /** 上一次运行结束到下一次开始之间的固定延迟（毫秒）。 */
    long fixedDelay() default -1;
    /** 连续两次运行开始之间的固定速率（毫秒）。 */
    long fixedRate() default -1;
    /** 首次运行前的初始延迟（毫秒）。 */
    long initialDelay() default 0;
    /** cron 表达式（5 字段：分 时 日 月 周）。 */
    String cron() default "";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Schedules {
    Scheduled[] value();
}
