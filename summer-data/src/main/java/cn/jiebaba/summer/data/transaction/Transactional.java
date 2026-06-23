package cn.jiebaba.summer.data.transaction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Transactional {
    String value() default "";
    /** Rollback for these exception types (default: any RuntimeException or Error). */
    Class<? extends Throwable>[] rollbackFor() default {};
    /** Do not rollback for these exception types. */
    Class<? extends Throwable>[] noRollbackFor() default {};
    boolean readOnly() default false;
}
