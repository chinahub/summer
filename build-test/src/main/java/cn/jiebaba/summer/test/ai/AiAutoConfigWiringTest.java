package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.ai.retry.ResilientChatModel;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.AfterEach;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Test;

import java.util.Set;

/** summer-boot AiAutoConfiguration 弹性包装的装配测试（独立作用域上下文，不启动 Web/DB）。 */
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
            Assert.assertNotNull(model);
            Assert.assertTrue(model instanceof OpenAiCompatibleChatModel,
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
            Assert.assertNotNull(model);
            Assert.assertTrue(model instanceof ResilientChatModel,
                    "配置重试时应包装为 ResilientChatModel，实际: " + model.getClass().getSimpleName());
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
