package cn.jiebaba.summer.core.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * summer 容器测试组合注解：与 JUnit 5 整合（参照 Spring {@code @SpringBootTest}、
 * Quarkus {@code @QuarkusTest}、Helidon {@code @HelidonTest} 的整合形态），
 * 元注解 {@link ExtendWith} 注册 {@link SummerExtension}，由真实 Jupiter 引擎执行。
 *
 * <p>标注后，每个测试类启动一个 summer IoC 容器（{@code DefaultApplicationContext}），
 * 测试实例的 {@code @Autowired} 字段与测试方法的 bean 类型参数会被自动注入：
 * <pre>{@code
 * @SummerTest("com.example.app")
 * class UserServiceTest {
 *     @Autowired UserService userService;
 *
 *     @Test
 *     void greeting() {
 *         Assertions.assertEquals("hi", userService.greet());
 *     }
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SummerExtension.class)
public @interface SummerTest {

    /** 容器组件扫描的基础包；缺省时取测试类所在包。 */
    String[] value() default {};
}
