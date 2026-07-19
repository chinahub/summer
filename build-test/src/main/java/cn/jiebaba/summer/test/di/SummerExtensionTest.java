package cn.jiebaba.summer.test.di;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.test.SummerTest;
import cn.jiebaba.summer.test.aop.it.GreetService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@code @SummerTest} 整合层示范：JUnit 5 引擎启动 summer 容器，
 * 验证 @Autowired 字段注入与测试方法参数的 bean 注入。
 */
@SummerTest("cn.jiebaba.summer.test.aop.it")
public class SummerExtensionTest {

    @Autowired
    private GreetService greetService;

    /** 字段注入：扩展在测试实例创建后按字段类型注入容器 bean。 */
    @Test
    void fieldInjectionWorks() {
        Assertions.assertNotNull(greetService, "@Autowired 字段应被容器注入");
        Assertions.assertEquals("hi", greetService.greet());
    }

    /** 参数注入：测试方法的 bean 类型参数由 ParameterResolver 解析。 */
    @Test
    void parameterInjectionWorks(GreetService injected) {
        Assertions.assertNotNull(injected, "方法参数应被容器解析注入");
        Assertions.assertEquals("hi", injected.greet());
    }
}
