package cn.jiebaba.summer.test.ioc;

import cn.jiebaba.summer.core.context.DefaultApplicationContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证 IoC 容器基础能力：单例检索、字段/构造注入、@Value、@Primary、@Bean 工厂方法。 */
public class IocContainerTest {

    private DefaultApplicationContext context;

    @BeforeEach
    void refreshContext() {
        context = new DefaultApplicationContext(null, null, Set.of("cn.jiebaba.summer.test.ioc"));
        context.refresh();
    }

    @Test
    @DisplayName("单例 Bean 按名称与类型检索为同一实例")
    void singletonRetrieval() {
        SingletonBean byType = context.getBean(SingletonBean.class);
        SingletonBean byName = context.getBean("singletonBean", SingletonBean.class);
        Assertions.assertNotNull(byType);
        Assertions.assertSame(byType, byName);
        Assertions.assertEquals("singleton", byType.id());
    }

    @Test
    void fieldInjection() {
        FieldInjectBean bean = context.getBean(FieldInjectBean.class);
        Assertions.assertNotNull(bean.dep());
        Assertions.assertEquals("singleton", bean.dep().id());
    }

    @Test
    void constructorInjection() {
        ConstructorInjectBean bean = context.getBean(ConstructorInjectBean.class);
        Assertions.assertNotNull(bean.dep());
    }

    @Test
    void valueInjectionWithDefault() {
        ValueBean bean = context.getBean(ValueBean.class);
        Assertions.assertEquals("fallback", bean.name());
    }

    @Test
    void primarySelectedWhenMultipleCandidates() {
        Greeter greeter = context.getBean(Greeter.class);
        Assertions.assertEquals("primary", greeter.greet());
    }

    @Test
    void beanFactoryMethod() {
        Gadget gadget = context.getBean(Gadget.class);
        Assertions.assertEquals("factory", gadget.tag);
    }

    @Test
    void containsBean() {
        Assertions.assertTrue(context.containsBean("singletonBean"));
        Assertions.assertFalse(context.containsBean("noSuchBean"));
    }
}
