package cn.jiebaba.summer.data.annotation;

import cn.jiebaba.summer.data.support.TypeHandler;

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
    /** 该列的自定义 TypeHandler（如 JsonTypeHandler）。默认无。 */
    Class<? extends TypeHandler> typeHandler() default TypeHandler.class;
}
