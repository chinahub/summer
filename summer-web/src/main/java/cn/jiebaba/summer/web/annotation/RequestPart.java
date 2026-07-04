package cn.jiebaba.summer.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bind a method parameter to a part of a {@code multipart/form-data} request:
 * a file part ({@code MultipartFile}) or a regular form field. Mirrors Spring's
 * {@code @RequestPart}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestPart {
    String value() default "";
    String name() default "";
    boolean required() default true;
}