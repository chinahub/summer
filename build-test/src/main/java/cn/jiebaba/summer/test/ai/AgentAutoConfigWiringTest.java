package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.AgentOrchestrator;
import cn.jiebaba.summer.ai.agent.AgentPolicy;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.ai.agent.AgentTask;
import cn.jiebaba.summer.boot.ai.AgentAutoConfiguration;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * AgentAutoConfiguration 装配测试（独立作用域上下文，不启动 Web/DB）：
 * 默认装配编排器与策略 bean；注入自定义 AgentExecutor 后 dispatch 可用；
 * {@code @ConditionalOnMissingBean} 退避生效；{@code summer.ai.enabled=false} 整体关闭。
 */
public class AgentAutoConfigWiringTest {

    /** 程序化注册 AgentAutoConfiguration（对齐 SummerApplication 的注册方式；扫描空包避免与框架包内组件重复注册）。 */
    private static DefaultApplicationContext agentContext() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                null, new Environment(), Set.of("cn.jiebaba.summer.nonexistent"));
        String name = DefaultApplicationContext.decapitalize(AgentAutoConfiguration.class.getSimpleName());
        ctx.registerBeanDefinition(name, new BeanDefinition(name, AgentAutoConfiguration.class));
        return ctx;
    }

    @BeforeEach
    void clearProps() {
        clearAgentProps();
    }

    @AfterEach
    void cleanup() {
        clearAgentProps();
    }

    @Test
    public void orchestratorAndPolicyWiredByDefault() {
        DefaultApplicationContext ctx = agentContext();
        try {
            ctx.refresh();
            Assertions.assertNotNull(ctx.getBean(AgentOrchestrator.class));
            AgentPolicy policy = ctx.getBean(AgentPolicy.class);
            Assertions.assertEquals(300, policy.timeoutSeconds());
            Assertions.assertEquals(8, policy.maxDepth());
        } finally {
            ctx.close();
        }
    }

    /** 上下文注入自定义 AgentExecutor 后，编排器可按技能名路由（执行器表惰性解析）。 */
    @Test
    public void dispatchWithCustomExecutor() {
        DefaultApplicationContext ctx = agentContext();
        try {
            ctx.registerBean("echoAgent", new AgentExecutor() {
                @Override
                public String name() {
                    return "echo";
                }

                @Override
                public AgentResult run(AgentTask task) {
                    return AgentResult.ok("echo:" + task.input(), "echo: exit=0");
                }
            });
            ctx.refresh();
            AgentResult result = ctx.getBean(AgentOrchestrator.class).dispatch("echo", "你好");
            Assertions.assertTrue(result.success(), result.error());
            Assertions.assertEquals("echo:你好", result.output());
        } finally {
            ctx.close();
        }
    }

    /** 应用自定义 AgentPolicy/AgentOrchestrator bean 时退避生效（保留先注册者）。 */
    @Test
    public void customBeansTakePrecedence() {
        DefaultApplicationContext ctx = agentContext();
        try {
            AgentPolicy custom = new AgentPolicy(1, 4);
            ctx.registerBean("agentPolicy", custom);
            ctx.refresh();
            Assertions.assertSame(custom, ctx.getBean(AgentPolicy.class),
                    "自定义 AgentPolicy 应退避框架默认装配");
        } finally {
            ctx.close();
        }
    }

    /** summer.ai.enabled=false 时整体关闭：不装配编排器与策略（与 AiAutoConfiguration 总开关约定一致）。 */
    @Test
    public void disabledBySummerAiEnabledFalse() {
        System.setProperty("summer.ai.enabled", "false");
        DefaultApplicationContext ctx = agentContext();
        try {
            ctx.refresh();
            Assertions.assertThrows(RuntimeException.class,
                    () -> ctx.getBean(AgentOrchestrator.class), "总开关关闭时不应装配编排器");
            Assertions.assertThrows(RuntimeException.class,
                    () -> ctx.getBean(AgentPolicy.class), "总开关关闭时不应装配策略");
        } finally {
            ctx.close();
        }
    }

    private void clearAgentProps() {
        java.util.Properties sys = System.getProperties();
        sys.stringPropertyNames().stream()
                .filter(n -> n.startsWith("summer.ai."))
                .toList()
                .forEach(sys::remove);
    }
}
