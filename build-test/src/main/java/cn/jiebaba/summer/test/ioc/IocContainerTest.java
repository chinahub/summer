package cn.jiebaba.summer.test.ioc;

import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.DisplayName;
import cn.jiebaba.summer.core.test.Test;

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
        Assert.assertNotNull(byType);
        Assert.assertSame(byType, byName);
        Assert.assertEquals("singleton", byType.id());
    }

    @Test
    void fieldInjection() {
        FieldInjectBean bean = context.getBean(FieldInjectBean.class);
        Assert.assertNotNull(bean.dep());
        Assert.assertEquals("singleton", bean.dep().id());
    }

    @Test
    void constructorInjection() {
        ConstructorInjectBean bean = context.getBean(ConstructorInjectBean.class);
        Assert.assertNotNull(bean.dep());
    }

    @Test
    void valueInjectionWithDefault() {
        ValueBean bean = context.getBean(ValueBean.class);
        Assert.assertEquals("fallback", bean.name());
    }

    @Test
    void primarySelectedWhenMultipleCandidates() {
        Greeter greeter = context.getBean(Greeter.class);
        Assert.assertEquals("primary", greeter.greet());
    }

    @Test
    void beanFactoryMethod() {
        Gadget gadget = context.getBean(Gadget.class);
        Assert.assertEquals("factory", gadget.tag);
    }

    @Test
    void containsBean() {
        Assert.assertTrue(context.containsBean("singletonBean"));
        Assert.assertFalse(context.containsBean("noSuchBean"));
    }
}
