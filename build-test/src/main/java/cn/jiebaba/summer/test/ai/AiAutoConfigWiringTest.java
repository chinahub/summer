package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.ai.retry.ResilientChatModel;
import cn.jiebaba.summer.ai.tools.Tool;
import cn.jiebaba.summer.ai.tools.ToolCallback;
import cn.jiebaba.summer.ai.tools.ToolCallingChatModel;
import cn.jiebaba.summer.ai.tools.ToolParameter;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** summer-boot AiAutoConfiguration 弹性包装与工具调用的装配测试（独立作用域上下文，不启动 Web/DB）。 */
public class AiAutoConfigWiringTest {

    @BeforeEach
    void clearProps() {
        clearAiProps();
    }

    @AfterEach
    void cleanup() {
        clearAiProps();
    }

    @Test
    public void plainChatModelWhenNoResilienceConfig() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ctx.refresh();
            ChatModel model = ctx.getBean(ChatModel.class);
            Assertions.assertNotNull(model);
            Assertions.assertTrue(model instanceof OpenAiCompatibleChatModel,
                    "未配置弹性策略时应为原始实现，实际: " + model.getClass().getSimpleName());
        } finally {
            ctx.close();
        }
    }

    @Test
    public void resilientChatModelWhenRetryConfigured() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        System.setProperty("summer.ai.retry.max-attempts", "3");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ctx.refresh();
            ChatModel model = ctx.getBean(ChatModel.class);
            Assertions.assertNotNull(model);
            Assertions.assertTrue(model instanceof ResilientChatModel,
                    "配置重试时应包装为 ResilientChatModel，实际: " + model.getClass().getSimpleName());
        } finally {
            ctx.close();
        }
    }

    /** tools.enabled=true 且上下文存在 ToolCallback bean 时，ChatModel 应叠加 ToolCallingChatModel。 */
    @Test
    public void toolCallingChatModelWiredWhenEnabledAndToolBeanPresent() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        System.setProperty("summer.ai.tools.enabled", "true");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ToolCallback echo = new Tool("echo", "回显文本",
                    List.of(ToolParameter.string("text", "待回显文本")),
                    args -> Map.of("echo", args.get("text")));
            ctx.registerBean("echoTool", echo);
            ctx.refresh();
            ChatModel model = ctx.getBean(ChatModel.class);
            Assertions.assertTrue(model instanceof ToolCallingChatModel,
                    "tools.enabled=true 且存在 ToolCallback bean 时应包装为 ToolCallingChatModel，实际: "
                            + model.getClass().getSimpleName());
            Assertions.assertFalse(ctx.getBeansOfType(ToolCallback.class).isEmpty(),
                    "应收集到 ToolCallback bean");
        } finally {
            ctx.close();
        }
    }

    /** tools.enabled=true 但无 ToolCallback bean 时，不应包装为 ToolCallingChatModel（保持原始/弹性实现）。 */
    @Test
    public void toolsEnabledWithoutToolBeanStaysPlain() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        System.setProperty("summer.ai.tools.enabled", "true");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ctx.refresh();
            ChatModel model = ctx.getBean(ChatModel.class);
            Assertions.assertFalse(model instanceof ToolCallingChatModel,
                    "无 ToolCallback bean 时不应包装为 ToolCallingChatModel，实际: " + model.getClass().getSimpleName());
        } finally {
            ctx.close();
        }
    }

    private void clearAiProps() {
        java.util.Properties sys = System.getProperties();
        sys.stringPropertyNames().stream()
                .filter(n -> n.startsWith("summer.ai."))
                .toList()
                .forEach(sys::remove);
    }
}
