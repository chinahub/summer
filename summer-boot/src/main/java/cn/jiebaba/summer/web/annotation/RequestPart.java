package cn.jiebaba.summer.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将方法参数绑定到 {@code multipart/form-data} 请求的一个部分：
 * 文件部分（{@code MultipartFile}）或普通表单字段。对应 Spring 的 {@code @RequestPart}。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestPart {
    String value() default "";
    String name() default "";
    boolean required() default true;
}
