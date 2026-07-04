package cn.jiebaba.summer.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 将方法标记为测试用例。必须为 void 且无参数。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Test {
    /** 设为具体异常类型时，测试预期抛出该异常；Throwable.class 表示"无预期"。 */
    Class<? extends Throwable> expected() default Throwable.class;
}
