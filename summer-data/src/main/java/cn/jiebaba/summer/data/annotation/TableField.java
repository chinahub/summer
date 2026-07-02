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
    /** Custom TypeHandler for this column (e.g. JsonTypeHandler). Defaults to none. */
    Class<? extends TypeHandler> typeHandler() default TypeHandler.class;
}