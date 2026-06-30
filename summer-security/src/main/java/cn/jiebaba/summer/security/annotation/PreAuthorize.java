package cn.jiebaba.summer.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level authorization, enforced by the web layer after route matching.
 * <p>Unlike Spring Security's expression-based {@code @PreAuthorize} (which relies
 * on SpEL), summer uses a declarative form: list {@code roles} and/or
 * {@code authorities}; {@code requireAll} selects AND vs OR semantics.
 * <p>Only effective on web handler methods (controller methods reachable via routing);
 * see {@code docs/security.md} for the rationale on service-layer scope.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PreAuthorize {
    /** Bare role names (without the {@code ROLE_} prefix); matched against {@code ROLE_<name>} authorities. */
    String[] roles() default {};

    /** Exact authority strings to match (e.g. {@code "user:read"}). */
    String[] authorities() default {};

    /** When {@code true} (default), ALL listed roles/authorities are required; when {@code false}, ANY one suffices. */
    boolean requireAll() default true;
}
