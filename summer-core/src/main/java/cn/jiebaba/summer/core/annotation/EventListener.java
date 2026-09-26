package cn.jiebaba.summer.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法为事件监听器：容器发布匹配事件时调用该方法。
 * 对齐 Spring 的 {@code @EventListener}。
 * <ul>
 *   <li>{@link #value()} 非空：按声明的事件类型（含子类）匹配；</li>
 *   <li>缺省时从方法唯一参数类型推断事件类型（无参方法监听所有事件类型）；</li>
 *   <li>{@link #async()} 为 true：在独立虚拟线程异步执行（异常隔离，仅记录日志），
 *       适合扇出派发等不要求调用方等待的场景；默认同步执行。</li>
 * </ul>
 * 同步监听器异常会向发布方传播（与 Spring 语义一致）；监听器 Bean 惰性创建。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {

    /** 监听的事件类型（可赋值匹配，支持子类）；未指定时退避到方法唯一参数类型。 */
    Class<?>[] value() default {};

    /** 是否异步执行（独立虚拟线程 + 异常隔离）；默认同步执行。 */
    boolean async() default false;
}
