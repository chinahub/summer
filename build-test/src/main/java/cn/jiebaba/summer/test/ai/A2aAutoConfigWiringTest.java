package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.agent.A2aCoordinator;
import cn.jiebaba.summer.ai.agent.A2aGateway;
import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.ContractStore;
import cn.jiebaba.summer.boot.ai.A2aAutoConfiguration;
import cn.jiebaba.summer.core.annotation.Controller;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

/**
 * A2aAutoConfiguration 装配测试（独立作用域上下文，不启动 Web）：
 * enabled=true 时装配契约存储/协调器/网关/端点 bean，且端点可被路由扫描发现
 * （WebRouteRegistrar 按 getBeansWithAnnotation(Controller.class) 扫描）；
 * 未开启时零 bean 注册（opt-in）。
 */
public class A2aAutoConfigWiringTest {

    @BeforeEach
    void clearProps() {
        System.clearProperty("summer.ai.a2a.enabled");
        System.clearProperty("summer.ai.provider");
        System.clearProperty("summer.ai.api-key");
    }

    @AfterEach
    void cleanup() {
        clearProps();
    }

    /**
     * 程序化注册 A2aAutoConfiguration（扫描空包避免与框架包内组件重复注册；
     * 生产环境由 SummerApplication 按同样方式注册，业务包扫描不会碰到框架包）。
     */
    private DefaultApplicationContext contextWithA2aConfig() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                null, new Environment(), Set.of("cn.jiebaba.summer.nonexistent"));
        String name = DefaultApplicationContext.decapitalize(A2aAutoConfiguration.class.getSimpleName());
        ctx.registerBeanDefinition(name, new BeanDefinition(name, A2aAutoConfiguration.class));
        return ctx;
    }

    @Test
    public void beansWiredWhenEnabled() {
        System.setProperty("summer.ai.a2a.enabled", "true");
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        System.setProperty("summer.ai.a2a.peer-url", "http://127.0.0.1:65501");
        DefaultApplicationContext ctx = contextWithA2aConfig();
        try {
            ctx.refresh();
            Assertions.assertNotNull(ctx.getBean(A2aCoordinator.class));
            Assertions.assertNotNull(ctx.getBean(ContractStore.class));
            Assertions.assertNotNull(ctx.getBean(A2aGateway.class));
            Assertions.assertNotNull(ctx.getBean(cn.jiebaba.summer.boot.ai.A2aEndpoints.class));
            // 端点 bean 带 @RestController（元注解 @Controller）→ WebRouteRegistrar 可发现并注册路由
            Map<String, Object> controllers = ctx.getBeansWithAnnotation(Controller.class);
            Assertions.assertTrue(controllers.values().stream()
                    .anyMatch(b -> b instanceof cn.jiebaba.summer.boot.ai.A2aEndpoints),
                    "路由扫描应能发现 A2aEndpoints: " + controllers.keySet());
            // 网关在执行器表中（业务可按 "a2a" 技能名路由），但接域执行表由协调器惰性排除
            Map<String, AgentExecutor> executors = ctx.getBeansOfType(AgentExecutor.class);
            Assertions.assertTrue(executors.values().stream().anyMatch(e -> e instanceof A2aGateway));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void noBeansWhenDisabled() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        DefaultApplicationContext ctx = contextWithA2aConfig();
        try {
            ctx.refresh();
            // 条件未满足时 A2aCoordinator 类型完全没有 bean 定义（getBeansOfType 对未知类型会抛错）
            Assertions.assertThrows(RuntimeException.class,
                    () -> ctx.getBean(A2aCoordinator.class), "未开启时不应装配协调器");
            Assertions.assertThrows(RuntimeException.class,
                    () -> ctx.getBean(cn.jiebaba.summer.boot.ai.A2aEndpoints.class), "未开启时不应注册端点");
        } finally {
            ctx.close();
        }
    }
}
