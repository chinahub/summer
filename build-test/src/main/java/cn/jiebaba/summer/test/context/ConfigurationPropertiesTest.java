package cn.jiebaba.summer.test.context;

import cn.jiebaba.summer.core.annotation.ConfigurationProperties;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code @ConfigurationProperties} 声明式绑定的单测：简单类型/kebab 键/List/Map/嵌套对象。 */
public class ConfigurationPropertiesTest {

    @AfterEach
    void cleanup() {
        System.getProperties().keySet().stream()
                .map(Object::toString)
                .filter(n -> n.startsWith("summer.test.props."))
                .toList()
                .forEach(n -> System.clearProperty(n));
    }

    @ConfigurationProperties("summer.test.props")
    static class Props {
        private String name;
        private int maxCount;
        private boolean enabled;
        private List<String> tags;
        private Map<String, String> labels;
        private Nested nested;

        String name() { return name; }
        int maxCount() { return maxCount; }
        boolean enabled() { return enabled; }
        List<String> tags() { return tags; }
        Map<String, String> labels() { return labels; }
        Nested nested() { return nested; }

        static class Nested {
            private String value;
            String value() { return value; }
        }
    }

    @Test
    public void bindAllSupportedFieldTypes() {
        System.setProperty("summer.test.props.name", "app");
        System.setProperty("summer.test.props.max-count", "7");
        System.setProperty("summer.test.props.enabled", "true");
        System.setProperty("summer.test.props.tags", "a,b,c");
        System.setProperty("summer.test.props.labels.x", "1");
        System.setProperty("summer.test.props.labels.y", "2");
        System.setProperty("summer.test.props.nested.value", "deep");

        DefaultApplicationContext ctx = new DefaultApplicationContext(null, new Environment(), Set.of());
        try {
            ctx.registerBeanDefinition("props", new BeanDefinition("props", Props.class));
            ctx.refresh();
            Props props = (Props) ctx.getBean("props");
            Assertions.assertEquals("app", props.name());
            Assertions.assertEquals(7, props.maxCount());
            Assertions.assertTrue(props.enabled());
            Assertions.assertEquals(List.of("a", "b", "c"), props.tags());
            Assertions.assertEquals(Map.of("x", "1", "y", "2"), props.labels());
            Assertions.assertEquals("deep", props.nested().value());
        } finally {
            ctx.close();
        }
    }

    @Test
    public void unboundFieldsKeepDefaults() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, new Environment(), Set.of());
        try {
            ctx.registerBeanDefinition("props", new BeanDefinition("props", Props.class));
            ctx.refresh();
            Props props = (Props) ctx.getBean("props");
            Assertions.assertNull(props.name());
            Assertions.assertEquals(0, props.maxCount());
            Assertions.assertFalse(props.enabled());
            Assertions.assertNull(props.tags());
            Assertions.assertNull(props.nested());
        } finally {
            ctx.close();
        }
    }
}
