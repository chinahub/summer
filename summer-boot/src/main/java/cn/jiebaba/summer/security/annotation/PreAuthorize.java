package cn.jiebaba.summer.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级授权，由 Web 层在路由匹配后强制执行。
 * <p>与 Spring Security 基于 SpEL 表达式的 {@code @PreAuthorize} 不同，summer 采用声明式：
 * 列出 {@code roles} 和/或 {@code authorities}；{@code requireAll} 选择 AND/OR 语义。
 * <p>仅对 Web 处理器方法（经路由可达的控制器方法）生效；
 * 关于服务层范围的考量见 {@code docs/security.md}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PreAuthorize {
    /** 裸角色名（不含 {@code ROLE_} 前缀）；与 {@code ROLE_<name>} 权限匹配。 */
    String[] roles() default {};

    /** 需精确匹配的权限字符串（如 {@code "user:read"}）。 */
    String[] authorities() default {};

    /** 为 {@code true}（默认）时需满足全部角色/权限；为 {@code false} 时满足其一即可。 */
    boolean requireAll() default true;
}
