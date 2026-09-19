package cn.jiebaba.summer.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 {@code @Bean} 工厂方法仅在容器中<b>不存在</b>指定类型的 Bean 时才注册（缺省退避），
 * 用于自动配置允许应用替换默认实现，对齐 Spring Boot 的 {@code @ConditionalOnMissingBean} 习惯。
 * <p>评估时机：容器在注册所有普通 {@code @Bean} 定义<b>之后</b>才处理带此注解的方法，
 * 因此用户通过组件扫描或工厂方法定义的同类型 Bean 均已可见。
 * <ul>
 *   <li>{@link #value()} 非空：按指定类型检查（任一类型已存在则跳过）；</li>
 *   <li>{@link #name()} 非空：按 Bean 名称检查（任一名称已存在则跳过）；</li>
 *   <li>均未指定：按工厂方法的返回类型检查。</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConditionalOnMissingBean {
    /** 检查的类型（可赋值匹配，支持子类）；未指定时退避到工厂方法返回类型。 */
    Class<?>[] value() default {};

    /** 检查的 Bean 名称（按名称精确匹配）；未指定时不按名称检查。 */
    String[] name() default {};
}
