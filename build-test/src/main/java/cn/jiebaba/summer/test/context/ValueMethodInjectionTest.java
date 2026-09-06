package cn.jiebaba.summer.test.context;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.Value;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 方法级 {@code @Value} 注入的补全测试：单参数 setter 注入配置值并完成类型转换。 */
public class ValueMethodInjectionTest {

    @AfterEach
    void cleanup() {
        System.getProperties().keySet().stream()
                .map(Object::toString)
                .filter(n -> n.startsWith("summer.test.value."))
                .toList()
                .forEach(n -> System.clearProperty(n));
    }

    @Component
    static class SetterBean {
        private String name;
        private int count;

        @Value("summer.test.value.name")
        public void setName(String name) { this.name = name; }

        @Value("summer.test.value.count")
        public void setCount(int count) { this.count = count; }

        String name() { return name; }
        int count() { return count; }
    }

    @Test
    public void valueSetterInjectionWithConversion() {
        System.setProperty("summer.test.value.name", "hello");
        System.setProperty("summer.test.value.count", "42");
        DefaultApplicationContext ctx =
                new DefaultApplicationContext(null, new Environment(), Set.of());
        try {
            ctx.registerBeanDefinition("setterBean", new BeanDefinition("setterBean", SetterBean.class));
            ctx.refresh();
            SetterBean bean = (SetterBean) ctx.getBean("setterBean");
            Assertions.assertEquals("hello", bean.name());
            Assertions.assertEquals(42, bean.count());
        } finally {
            ctx.close();
        }
    }
}
