package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.aop.SummerProxy;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

public class ContainerIntegrationTest {

    private DefaultApplicationContext fresh() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, null, Set.of("cn.jiebaba.summer.test.aop.it"));
        ctx.refresh();
        return ctx;
    }

    @Test
    void noInterfaceBeanGetsSubclassProxyWithInjectionAndInit() {
        DefaultApplicationContext ctx = fresh();
        NoInterfaceService svc = ctx.getBean(NoInterfaceService.class);
        Assertions.assertTrue(svc instanceof SummerProxy, "no-interface advised bean must be a subclass proxy");
        Assertions.assertTrue(svc.depInjected(), "@Autowired setter must run on the proxy");
        Assertions.assertTrue(svc.inited(), "@PostConstruct must run on the proxy");
        Assertions.assertEquals("worked", svc.work());
        Assertions.assertTrue(ctx.getBean(CounterAspect.class).count > 0, "aspect advice must have run");
        ctx.close();
    }

    @Test
    void interfaceBeanStillUsesJdkProxy() {
        DefaultApplicationContext ctx = fresh();
        Object bean = ctx.getBean("greetServiceImpl");
        Assertions.assertTrue(Proxy.isProxyClass(bean.getClass()), "interface bean must use a JDK proxy");
        Assertions.assertFalse(bean instanceof SummerProxy, "interface bean must not be a subclass proxy");
        Assertions.assertEquals("hi", ctx.getBean(GreetService.class).greet());
        ctx.close();
    }

    @Test
    void getTypeReturnsUserClassForSubclassProxy() {
        DefaultApplicationContext ctx = fresh();
        Assertions.assertEquals(NoInterfaceService.class, ctx.getType("noInterfaceService"));
        String[] names = ctx.getBeanNamesForType(NoInterfaceService.class);
        Assertions.assertTrue(names.length >= 1, "subclass proxy must be discoverable by user type");
        ctx.close();
    }

    @Test
    void proxyAdvisorAdvisesNoInterfaceBean() {
        DefaultApplicationContext ctx = fresh();
        AdvisedTarget target = ctx.getBean(AdvisedTarget.class);
        Assertions.assertTrue(target instanceof SummerProxy, "ProxyAdvisor-advised no-interface bean must be a subclass proxy");
        Assertions.assertEquals("pong", target.ping());
        Assertions.assertTrue(ctx.getBean(CountingAdvisor.class).count > 0, "ProxyAdvisor interceptor must have run");
        ctx.close();
    }

    @Test
    void proxyAdvisorOnInterfaceBeanUsesJdkProxy() {
        DefaultApplicationContext ctx = fresh();
        Object bean = ctx.getBean("pingedImpl");
        Assertions.assertTrue(Proxy.isProxyClass(bean.getClass()), "ProxyAdvisor on an interface bean must use a JDK proxy");
        Assertions.assertFalse(bean instanceof SummerProxy, "interface bean must not be a subclass proxy");
        Assertions.assertEquals("pong2", ctx.getBean(Pinged.class).ping());
        ctx.close();
    }
}