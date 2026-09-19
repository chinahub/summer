package cn.jiebaba.summer.data.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指定方法或类使用的数据源。运行时通过 {@link DsContext} 解析，将
 * {@link DynamicDataSource#getConnection()} 路由到正确的底层连接池。
 * {@code @Master} 与 {@code @Slave} 为便捷别名。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {
    /** 数据源名称，例如 {@code "master"}、{@code "slave"}、{@code "log-db"}。 */
    String value();
}
