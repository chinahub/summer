package cn.jiebaba.summer.test.context;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.ConditionalOnClass;
import cn.jiebaba.summer.core.annotation.ConditionalOnMissingBean;
import cn.jiebaba.summer.core.annotation.ConditionalOnProperty;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.context.NoSuchBeanDefinitionException;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * 条件注解 {@code @ConditionalOnProperty} / {@code @ConditionalOnClass} 的注册语义测试：
 * 方法级与类级条件、havingValue/matchIfMissing、与 {@code @ConditionalOnMissingBean} 组合。
 */
public class ConditionalBeanTest {

    @AfterEach
    void cleanup() {
        System.getProperties().keySet().stream()
                .map(Object::toString)
                .filter(n -> n.startsWith("summer.test.cond."))
                .toList()
                .forEach(n -> System.clearProperty(n));
    }

    @Configuration
    static class MethodLevelConfig {

        @Bean
        @ConditionalOnProperty(name = "summer.test.cond.flag", havingValue = "on")
        public String gatedBean() { return "gated"; }

        @Bean
        @ConditionalOnProperty(name = "summer.test.cond.absent", matchIfMissing = true)
        public String matchIfMissingBean() { return "default"; }

        @Bean
        @ConditionalOnClass(name = "cn.jiebaba.summer.core.env.Environment")
        public String classPresentBean() { return "present"; }

        @Bean
        @ConditionalOnClass(name = "no.such.Clazz")
        public String classAbsentBean() { return "absent"; }

        @Bean
        @ConditionalOnProperty(name = "summer.test.cond.combo", havingValue = "on")
        @ConditionalOnMissingBean
        public Greeting comboBean() { return () -> "combo"; }
    }

    @Configuration
    @ConditionalOnProperty(name = "summer.test.cond.level", havingValue = "on")
    static class ClassLevelConfig {

        @Bean
        public String levelGatedBean() { return "level-gated"; }
    }

    interface Greeting { String hello(); }

    private DefaultApplicationContext newContext() {
        return new DefaultApplicationContext(null, new Environment(), Set.of());
    }

    private static void register(DefaultApplicationContext ctx, String name, Class<?> config) {
        ctx.registerBeanDefinition(name, new BeanDefinition(name, config));
    }

    @Test
    public void propertyConditionGatesBean() {
        System.setProperty("summer.test.cond.flag", "on");
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "methodLevelConfig", MethodLevelConfig.class);
            ctx.refresh();
            Assertions.assertEquals("gated", ctx.getBean("gatedBean"));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void propertyMismatchSkipsBean() {
        System.setProperty("summer.test.cond.flag", "off");
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "methodLevelConfig", MethodLevelConfig.class);
            ctx.refresh();
            Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> ctx.getBean("gatedBean"));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void matchIfMissingRegistersWhenPropertyAbsent() {
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "methodLevelConfig", MethodLevelConfig.class);
            ctx.refresh();
            Assertions.assertEquals("default", ctx.getBean("matchIfMissingBean"));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void classConditionGatesAllBeans() {
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "classLevelConfig", ClassLevelConfig.class);
            ctx.refresh();
            Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> ctx.getBean("levelGatedBean"));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void classConditionSatisfiedRegistersBeans() {
        System.setProperty("summer.test.cond.level", "on");
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "classLevelConfig", ClassLevelConfig.class);
            ctx.refresh();
            Assertions.assertEquals("level-gated", ctx.getBean("levelGatedBean"));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void classConditionAppliedWhenPresentAndAbsent() {
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "methodLevelConfig", MethodLevelConfig.class);
            ctx.refresh();
            Assertions.assertEquals("present", ctx.getBean("classPresentBean"));
            Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> ctx.getBean("classAbsentBean"));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void combinedPropertyAndMissingBeanConditions() {
        System.setProperty("summer.test.cond.combo", "on");
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "methodLevelConfig", MethodLevelConfig.class);
            ctx.refresh();
            // 无用户同类型 Bean：组合条件满足，注册
            Assertions.assertEquals("combo", ((Greeting) ctx.getBean("comboBean")).hello());
        } finally {
            ctx.close();
        }
    }

    @Test
    public void missingBeanConditionYieldsToUserBean() {
        System.setProperty("summer.test.cond.combo", "on");
        DefaultApplicationContext ctx = newContext();
        try {
            register(ctx, "methodLevelConfig", MethodLevelConfig.class);
            ctx.registerBean("userGreeting", (Greeting) () -> "user");
            ctx.refresh();
            // 用户已定义同类型 Bean：组合条件的退避部分不满足，comboBean 被跳过
            Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> ctx.getBean("comboBean"));
            Assertions.assertEquals("user", ((Greeting) ctx.getBean("userGreeting")).hello());
        } finally {
            ctx.close();
        }
    }
}
