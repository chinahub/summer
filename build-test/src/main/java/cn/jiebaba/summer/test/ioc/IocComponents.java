package cn.jiebaba.summer.test.ioc;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.Primary;
import cn.jiebaba.summer.core.annotation.Value;

/**
 * IoC 容器基础测试使用的组件（均为顶层包级类，以便 {@code ClassPathScanner} 扫描到）：
 * 单例、字段/构造注入、@Value、@Primary、@Bean 工厂方法。
 */
@Component
class SingletonBean {
    public String id() {
        return "singleton";
    }
}

@Component
class FieldInjectBean {
    @Autowired
    SingletonBean dep;

    public SingletonBean dep() {
        return dep;
    }
}

@Component
class ConstructorInjectBean {
    private final SingletonBean dep;

    @Autowired
    public ConstructorInjectBean(SingletonBean dep) {
        this.dep = dep;
    }

    public SingletonBean dep() {
        return dep;
    }
}

@Component
class ValueBean {
    @Value("${ioc.name:fallback}")
    String name;

    public String name() {
        return name;
    }
}

interface Greeter {
    String greet();
}

@Component
@Primary
class PrimaryGreeter implements Greeter {
    public String greet() {
        return "primary";
    }
}

@Component
class DefaultGreeter implements Greeter {
    public String greet() {
        return "default";
    }
}

class Gadget {
    public final String tag;

    public Gadget(String tag) {
        this.tag = tag;
    }
}

@Configuration
class FactoryConfig {
    @Bean
    public Gadget gadget() {
        return new Gadget("factory");
    }
}
