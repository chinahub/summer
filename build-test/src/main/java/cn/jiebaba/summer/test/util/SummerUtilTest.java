package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.context.DisposableBean;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.SummerUtil;

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
        Assert.assertTrue(SummerUtil.containsBean("hello"));
        Assert.assertTrue(SummerUtil.getBean("hello") == bean);
        Assert.assertEquals("hi", SummerUtil.getBean("hello", HelloBean.class).greet());
    }

    @Test
    public void registerByObjectUsesClassName() {
        HelloBean bean = new HelloBean();
        SummerUtil.registerBean(bean);
        Assert.assertTrue(SummerUtil.containsBean("helloBean"));
        Assert.assertTrue(SummerUtil.getBean(HelloBean.class) == bean);
        Assert.assertEquals(HelloBean.class, SummerUtil.getType("helloBean"));
    }

    @Test
    public void duplicateRegisterRejected() {
        SummerUtil.registerBean("dup", new HelloBean());
        try {
            SummerUtil.registerBean("dup", new HelloBean());
            Assert.fail("expected duplicate registration to throw");
        } catch (Exception expected) {
            // ok
        }
    }

    @Test
    public void unregisterByName() {
        HelloBean bean = new HelloBean();
        SummerUtil.registerBean("bye", bean);
        Assert.assertTrue(SummerUtil.unregisterBean("bye"));
        Assert.assertFalse(SummerUtil.containsBean("bye"));
        Assert.assertFalse(SummerUtil.unregisterBean("bye"));
    }

    @Test
    public void unregisterByType() {
        SummerUtil.registerBean("a", new HelloBean());
        SummerUtil.registerBean("b", new HelloBean());
        int removed = SummerUtil.unregisterBean(HelloBean.class);
        Assert.assertEquals(2, removed);
        Assert.assertFalse(SummerUtil.containsBean("a"));
        Assert.assertFalse(SummerUtil.containsBean("b"));
    }

    @Test
    public void unregisterInvokesDestroy() {
        LifecycleBean bean = new LifecycleBean();
        SummerUtil.registerBean("life", bean);
        Assert.assertFalse(bean.destroyed);
        SummerUtil.unregisterBean("life");
        Assert.assertTrue(bean.destroyed);
    }

    @Test
    public void getBeanNamesForType() {
        SummerUtil.registerBean("one", new HelloBean());
        String[] names = SummerUtil.getBeanNamesForType(HelloBean.class);
        Assert.assertTrue(names.length == 1, "expected 1 name, got " + java.util.Arrays.toString(names));
        Assert.assertEquals("one", names[0]);
    }

    @Test
    public void contextNotInitializedThrows() {
        SummerUtil.clearContext();
        try {
            SummerUtil.getBean("anything");
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // ok
        }
        SummerUtil.setContext(context);
    }
}