package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.context.DisposableBean;
import cn.jiebaba.summer.core.util.SummerUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class SummerUtilTest {

    public static class HelloBean {
        public String greet() { return "hi"; }
    }

    public static class LifecycleBean implements DisposableBean {
        boolean destroyed = false;
        public void destroy() { destroyed = true; }
    }

    private DefaultApplicationContext context;

    @BeforeEach
    public void setUp() {
        context = new DefaultApplicationContext(null, null, Set.of());
        SummerUtil.setContext(context);
    }

    @Test
    public void registerAndGetByName() {
        HelloBean bean = new HelloBean();
        SummerUtil.registerBean("hello", bean);
        Assertions.assertTrue(SummerUtil.containsBean("hello"));
        Assertions.assertTrue(SummerUtil.getBean("hello") == bean);
        Assertions.assertEquals("hi", SummerUtil.getBean("hello", HelloBean.class).greet());
    }

    @Test
    public void registerByObjectUsesClassName() {
        HelloBean bean = new HelloBean();
        SummerUtil.registerBean(bean);
        Assertions.assertTrue(SummerUtil.containsBean("helloBean"));
        Assertions.assertTrue(SummerUtil.getBean(HelloBean.class) == bean);
        Assertions.assertEquals(HelloBean.class, SummerUtil.getType("helloBean"));
    }

    @Test
    public void duplicateRegisterRejected() {
        SummerUtil.registerBean("dup", new HelloBean());
        try {
            SummerUtil.registerBean("dup", new HelloBean());
            Assertions.fail("expected duplicate registration to throw");
        } catch (Exception expected) {
            // ok
        }
    }

    @Test
    public void unregisterByName() {
        HelloBean bean = new HelloBean();
        SummerUtil.registerBean("bye", bean);
        Assertions.assertTrue(SummerUtil.unregisterBean("bye"));
        Assertions.assertFalse(SummerUtil.containsBean("bye"));
        Assertions.assertFalse(SummerUtil.unregisterBean("bye"));
    }

    @Test
    public void unregisterByType() {
        SummerUtil.registerBean("a", new HelloBean());
        SummerUtil.registerBean("b", new HelloBean());
        int removed = SummerUtil.unregisterBean(HelloBean.class);
        Assertions.assertEquals(2, removed);
        Assertions.assertFalse(SummerUtil.containsBean("a"));
        Assertions.assertFalse(SummerUtil.containsBean("b"));
    }

    @Test
    public void unregisterInvokesDestroy() {
        LifecycleBean bean = new LifecycleBean();
        SummerUtil.registerBean("life", bean);
        Assertions.assertFalse(bean.destroyed);
        SummerUtil.unregisterBean("life");
        Assertions.assertTrue(bean.destroyed);
    }

    @Test
    public void getBeanNamesForType() {
        SummerUtil.registerBean("one", new HelloBean());
        String[] names = SummerUtil.getBeanNamesForType(HelloBean.class);
        Assertions.assertTrue(names.length == 1, "expected 1 name, got " + java.util.Arrays.toString(names));
        Assertions.assertEquals("one", names[0]);
    }

    @Test
    public void contextNotInitializedThrows() {
        SummerUtil.clearContext();
        try {
            SummerUtil.getBean("anything");
            Assertions.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // ok
        }
        SummerUtil.setContext(context);
    }
}