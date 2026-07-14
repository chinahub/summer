package cn.jiebaba.summer.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 {@link ParameterizedTest} 提供字面量实参；按方法参数类型自动选用对应数组，
 * 例如参数为 {@code int} 选用 {@link #ints()}、为 {@code String} 选用 {@link #strings()}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueSource {
    int[] ints() default {};
    long[] longs() default {};
    double[] doubles() default {};
    String[] strings() default {};
    boolean[] booleans() default {};
    char[] chars() default {};
    Class<?>[] classes() default {};
}
