package cn.jiebaba.summer.data.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多数据源事务。与单数据源的 {@code @Transactional} 不同，它管理方法执行过程中涉及的
 * 多个数据源的连接：每个数据源各持一个连接（autoCommit=false），成功则全部提交，
 * 失败则全部回滚。
 *
 * <p>这是尽力而为的多库事务（非 2PC/XA）。若一个数据源提交失败前另一数据源已提交，
 * 则已提交的数据将保留。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DSTransactional {
    /** 对这些异常类型回滚（默认：任意 RuntimeException 或 Error）。 */
    Class<? extends Throwable>[] rollbackFor() default {};
    /** 对这些异常类型不回滚。 */
    Class<? extends Throwable>[] noRollbackFor() default {};
}
