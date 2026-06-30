package cn.jiebaba.summer.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the current authenticated principal to a handler method parameter.
 * The value injected is {@link cn.jiebaba.summer.security.core.Authentication#getPrincipal()},
 * typically the {@link cn.jiebaba.summer.security.userdetails.UserDetails} instance.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticationPrincipal {
    /** When true and no principal is present, inject null instead of failing the request. */
    boolean required() default true;
}
