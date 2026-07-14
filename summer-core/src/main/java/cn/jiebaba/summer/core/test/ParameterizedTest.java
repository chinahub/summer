package cn.jiebaba.summer.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将方法标记为参数化测试：方法须有且仅有一个参数，配合 {@link ValueSource} 提供的实参
 * 逐个运行，每个实参视为一次独立测试。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterizedTest {
}
