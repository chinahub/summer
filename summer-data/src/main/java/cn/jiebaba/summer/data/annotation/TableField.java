package cn.jiebaba.summer.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableField {
    String value() default "";
    boolean exist() default true;
    String insertStrategy() default "DEFAULT";
    String updateStrategy() default "DEFAULT";
}
