package cn.jiebaba.summer.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将当前已认证主体绑定到处理器方法参数。注入的值为
 * {@link cn.jiebaba.summer.security.core.Authentication#getPrincipal()}，
 * 通常是 {@link cn.jiebaba.summer.security.userdetails.UserDetails} 实例。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticationPrincipal {
    /** 为 true 且无主体时注入 null，而非令请求失败。 */
    boolean required() default true;
}
