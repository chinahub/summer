package cn.jiebaba.summer.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as a test case. Must be void with no parameters. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Test {
    /** If set to a concrete exception type, the test is expected to throw it. Throwable.class means "no expectation". */
    Class<? extends Throwable> expected() default Throwable.class;
}