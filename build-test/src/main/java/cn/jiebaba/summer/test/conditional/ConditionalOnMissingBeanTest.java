package cn.jiebaba.summer.test.conditional;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.ConditionalOnMissingBean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.context.NoSuchBeanDefinitionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * CORE-1 回归测试：带 {@code @ConditionalOnMissingBean} 的 @Bean 工厂方法
 * 在容器已有同类型（可赋值）/同名 Bean 时跳过注册，允许用户实现替换自动配置默认实现。
 */
public class ConditionalOnMissingBeanTest {

    private cn.jiebaba.summer.core.context.DefaultApplicationContext context;

    @BeforeEach
    void refreshContext() {
        context = new cn.jiebaba.summer.core.context.DefaultApplicationContext(
                null, null, Set.of("cn.jiebaba.summer.test.conditional"));
        context.refresh();
    }

    @Test
    @DisplayName("容器已有同类型组件时，退避 Bean 被跳过，用户实现生效")
    void sameTypeBackingOff() {
        Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> context.getBean("autoStore"),
                "conditional bean must be skipped when same type already registered");
        Assertions.assertEquals("custom", context.getBean(Store.class).name(),
                "user component must be the only Store candidate");
    }

    @Test
    @DisplayName("容器无同类型 Bean 时，退避 Bean 正常注册")
    void registersWhenTypeAbsent() {
        Assertions.assertEquals("auto", context.getBean(Widget.class).name(),
                "conditional bean must register when no same-type bean exists");
    }

    @Test
    @DisplayName("按注解 value 指定的精确类型退避，不被宽接口其他实现误触发")
    void preciseValueTypeBackingOff() {
        Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> context.getBean("preciseAuto"),
                "conditional bean must back off when precise value type exists");
        Assertions.assertFalse(context.getBeansOfType(Handler.class).isEmpty(),
                "other Handler implementations must not trigger back-off");
    }

    @Test
    @DisplayName("按注解 name 指定的名称退避")
    void nameBackingOff() {
        Assertions.assertThrows(NoSuchBeanDefinitionException.class, () -> context.getBean("namedStore"),
                "conditional bean must be skipped when named bean already registered");
    }

    @Test
    @DisplayName("普通 @Bean 方法不受影响，始终注册")
    void plainBeanAlwaysRegistered() {
        Assertions.assertNotNull(context.getBean("plain", Plain.class));
    }
}
