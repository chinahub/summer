package cn.jiebaba.summer.test.conditional;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.ConditionalOnMissingBean;
import cn.jiebaba.summer.core.annotation.Configuration;

/**
 * {@code ConditionalOnMissingBeanTest} 使用的组件（顶层包级类，供 {@code ClassPathScanner} 扫描）。
 */
interface Store {
    String name();
}

@Component
class CustomStore implements Store {
    @Override public String name() { return "custom"; }
}

class Widget {
    private final String name;

    Widget(String name) { this.name = name; }

    public String name() { return name; }
}

class Plain {
}

interface Handler {
}

@Component
class PreciseHandler implements Handler {
}

@Component
class OtherHandler implements Handler {
}

@Configuration
class ConditionalConfig {

    /** 容器已有 CustomStore（同类型）→ 应被跳过。 */
    @Bean
    @ConditionalOnMissingBean
    Store autoStore() {
        return new Store() {
            @Override public String name() { return "auto"; }
        };
    }

    /** 容器无 Widget 类型 Bean → 应正常注册。 */
    @Bean
    @ConditionalOnMissingBean
    Widget autoWidget() {
        return new Widget("auto");
    }

    /** 按精确类型退避：PreciseHandler 已注册 → 跳过；仅 OtherHandler 存在时不误触发。 */
    @Bean
    @ConditionalOnMissingBean(PreciseHandler.class)
    Handler preciseAuto() {
        return new Handler() {
        };
    }

    /** 按名称退避：plain 同配置类中先注册 → 第二轮评估时已存在 → 跳过。 */
    @Bean
    @ConditionalOnMissingBean(name = "plain")
    Widget namedWidget() {
        return new Widget("named");
    }

    /** 普通 @Bean 方法，不受退避机制影响。 */
    @Bean
    Plain plain() {
        return new Plain();
    }
}
