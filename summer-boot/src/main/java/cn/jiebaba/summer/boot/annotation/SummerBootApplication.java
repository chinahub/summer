package cn.jiebaba.summer.boot.annotation;

import cn.jiebaba.summer.core.annotation.ComponentScan;
import cn.jiebaba.summer.core.annotation.Configuration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Configuration
@ComponentScan
public @interface SummerBootApplication {
    String[] scanBasePackages() default {};
    Class<?>[] scanBasePackageClasses() default {};
}
