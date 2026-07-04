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
    /** 对这些异常类型回滚（默认：任意 RuntimeException 或 Error）。 */
    Class<? extends Throwable>[] rollbackFor() default {};
    /** 对这些异常类型不回滚。 */
    Class<? extends Throwable>[] noRollbackFor() default {};
    boolean readOnly() default false;
}
