package cn.jiebaba.summer.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * classpath 条件：指定的类全部存在时才注册对应 {@code @Bean}（方法级）或整个
 * {@code @Configuration}（类级）。对齐 Spring Boot 的 {@code @ConditionalOnClass}。
 * <p>仅支持按全限定类名字符串探测（内部 {@code Class.forName(name, false, ...)}），
 * 不提供 {@code Class} 字面量属性，避免在配置类中产生对可选模块的编译期依赖。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConditionalOnClass {

    /** 需要存在于 classpath 的类全限定名（全部存在才注册）。 */
    String[] name();
}
