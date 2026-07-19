package cn.jiebaba.summer.test.aop;

import cn.jiebaba.summer.core.aop.MethodInterceptor;
import cn.jiebaba.summer.core.aop.SubclassProxyFactory;
import cn.jiebaba.summer.core.aop.SummerProxy;
import cn.jiebaba.summer.core.context.BeansException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class SubclassProxyTest {

    private static Object proxy(Class<?> target, List<MethodInterceptor> interceptors) throws Exception {
        Constructor<?> ctor = target.getDeclaredConstructor();
        ctor.setAccessible(true);
        return SubclassProxyFactory.create(target, ctor, new Object[0], interceptors, List.of());
    }

    @Test
    void simpleProxyReturnsSuperValue() throws Exception {
        ProxyTargets.Simple s = (ProxyTargets.Simple) proxy(ProxyTargets.Simple.class, List.of());
        Assertions.assertEquals("hello world", s.greet("world"));
    }

    @Test
    void proxyIsSubclassAndMarker() throws Exception {
        Object p = proxy(ProxyTargets.Simple.class, List.of());
        Assertions.assertTrue(p instanceof ProxyTargets.Simple);
        Assertions.assertTrue(p instanceof SummerProxy);
        Assertions.assertEquals(ProxyTargets.Simple.class, p.getClass().getSuperclass());
    }

    @Test
    void returnTypesRoundTripThroughBoxing() throws Exception {
        ProxyTargets.ReturnTypes r = (ProxyTargets.ReturnTypes) proxy(ProxyTargets.ReturnTypes.class, List.of());
        Assertions.assertEquals(5, r.addInt(2, 3));
        Assertions.assertEquals(16L, r.squareLong(4));
        Assertions.assertEquals(false, r.neg(true));
        String[] arr = r.dup("x");
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("x", arr[0]);
        r.noop();
    }

    @Test
    void finalMethodCallableButNotOverridden() throws Exception {
        ProxyTargets.FinalMethod f = (ProxyTargets.FinalMethod) proxy(ProxyTargets.FinalMethod.class, List.of());
        Assertions.assertEquals("final", f.fin());
        Assertions.assertEquals("normal", f.normal());
    }

    @Test
    void interceptorAppliesAndReturnPasses() throws Exception {
        List<String> calls = new ArrayList<>();
        MethodInterceptor rec = jp -> { calls.add(jp.getMethod().getName()); return jp.proceed(); };
        ProxyTargets.Simple s = (ProxyTargets.Simple) proxy(ProxyTargets.Simple.class, List.of(rec));
        Assertions.assertEquals("hello summer", s.greet("summer"));
        Assertions.assertTrue(calls.contains("greet"), "interceptor must run");
    }

    @Test
    void selfInvocationIsInterceptedNoRecursion() throws Exception {
        List<String> calls = new ArrayList<>();
        MethodInterceptor rec = jp -> { calls.add(jp.getMethod().getName()); return jp.proceed(); };
        ProxyTargets.SelfCall s = (ProxyTargets.SelfCall) proxy(ProxyTargets.SelfCall.class, List.of(rec));
        Assertions.assertEquals("b-a", s.a());
        Assertions.assertTrue(calls.contains("a"), "a() must be intercepted");
        Assertions.assertTrue(calls.contains("b"), "self-invoked b() must be intercepted (no recursion)");
    }

    @Test
    void finalClassThrows() throws Exception {
        Assertions.assertThrows(cn.jiebaba.summer.core.context.BeansException.class, () -> {
            proxy(ProxyTargets.CannotSubclass.class, List.of());
        });
    }
}
