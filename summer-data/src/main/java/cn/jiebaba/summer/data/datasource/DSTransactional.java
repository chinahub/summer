package cn.jiebaba.summer.data.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Multi-datasource transaction. Unlike single-source {@code @Transactional}, this
 * manages connections across multiple datasources encountered during the method
 * execution. Each datasource gets its own connection (autoCommit=false); on
 * success all are committed, on failure all are rolled back.
 *
 * <p>This is a best-effort multi-DB transaction (not 2PC/XA). If commit fails on
 * one datasource after another has committed, the already-committed data remains.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DSTransactional {
    /** Rollback for these exception types (default: any RuntimeException or Error). */
    Class<? extends Throwable>[] rollbackFor() default {};
    /** Do not rollback for these exception types. */
    Class<? extends Throwable>[] noRollbackFor() default {};
}