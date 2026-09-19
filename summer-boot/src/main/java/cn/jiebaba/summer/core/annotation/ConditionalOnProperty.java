package cn.jiebaba.summer.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置条件：指定的配置项全部满足时才注册对应 {@code @Bean}（方法级）或整个
 * {@code @Configuration}（类级）。对齐 Spring Boot 的 {@code @ConditionalOnProperty}。
 * <ul>
 *   <li>{@link #havingValue()} 为空：配置项存在且值不等于 {@code "false"} 即满足；</li>
 *   <li>{@link #havingValue()} 非空：配置值（忽略大小写）必须与之相等；</li>
 *   <li>配置项缺失：按 {@link #matchIfMissing()} 决定（默认不满足）。</li>
 * </ul>
 * 多个 {@link #name()} 需全部满足。评估发生在普通 {@code @Bean} 注册阶段，
 * 可与 {@link ConditionalOnMissingBean} 组合（后者在全部普通 Bean 注册后才评估）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConditionalOnProperty {

    /** 需要检查的配置键（全部满足才注册）。 */
    String[] name();

    /** 期望的配置值；为空表示仅需存在且不为 "false"。 */
    String havingValue() default "";

    /** 配置项缺失时是否视为满足（默认 false：缺失即不注册）。 */
    boolean matchIfMissing() default false;
}
