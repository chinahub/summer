package cn.jiebaba.summer.data.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies which datasource to use for a method or class. Resolved at runtime
 * via {@link DsContext} to route {@link DynamicDataSource#getConnection()} to
 * the correct underlying pool. {@code @Master} and {@code @Slave} are
 * convenience aliases.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {
    /** Datasource name, e.g. {@code "master"}, {@code "slave"}, {@code "log-db"}. */
    String value();
}