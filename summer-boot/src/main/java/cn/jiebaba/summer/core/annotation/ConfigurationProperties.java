package cn.jiebaba.summer.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式配置绑定：标注在组件类上，容器在属性注入完成后按前缀将
 * {@code prefix.field} 形式的配置键绑定到同名（驼峰转 kebab-case）字段。
 * 支持简单类型（String/基本类型及包装/枚举）、{@code List<T>}（逗号分隔或
 * {@code key[0]} 索引形式）、{@code Map<String,String>}（前缀下所有子键）与
 * 嵌套 POJO（默认构造 + 递归绑定，深度上限 8 层）。
 * 未配置的键保持字段默认值。对齐 Spring Boot 的 {@code @ConfigurationProperties}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigurationProperties {

    /** 配置前缀；为空时使用类名的 kebab-case 形式（如 AppConfig → app-config）。 */
    String value() default "";
}
