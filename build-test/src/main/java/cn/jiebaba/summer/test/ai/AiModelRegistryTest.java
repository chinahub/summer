package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.boot.ai.AiAutoConfiguration;
import cn.jiebaba.summer.boot.ai.AiModelRegistry;
import cn.jiebaba.summer.boot.ai.NamedAiModel;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.context.BeanDefinition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * 多模型注册表集成测试（summer.ai.models.&lt;id&gt;.*）：命名实例并存且可按 id 取用、
 * 主模型未配置时回退注册表默认实例、实例配置不完整/全未配置时启动快速失败。
 */
public class AiModelRegistryTest {

    private static final String REQ_PROVIDER = "--summer.ai.models.requirement.provider=deepseek";
    private static final String REQ_KEY = "--summer.ai.models.requirement.api-key=sk-test-deepseek";
    private static final String PRD_PROVIDER = "--summer.ai.models.product.provider=kimi";
    private static final String PRD_KEY = "--summer.ai.models.product.api-key=sk-test-kimi";

    /** 以给定命令行参数启动仅含 AiAutoConfiguration 的容器（build-test classpath 无 summer.ai 文件层配置）。 */
    private static DefaultApplicationContext contextWith(String... args) {
        Environment env = new Environment(args);
        DefaultApplicationContext context = new DefaultApplicationContext(null, env, Set.of());
        BeanDefinition def = new BeanDefinition(
                DefaultApplicationContext.decapitalize(AiAutoConfiguration.class.getSimpleName()),
                AiAutoConfiguration.class);
        context.registerBeanDefinition(def.getName(), def);
        context.refresh();
        return context;
    }

    @Test
    @DisplayName("两个命名实例并存：按 id 取用、id 集合正确、首实例为默认")
    void namedInstancesCoexist() {
        DefaultApplicationContext context = contextWith(REQ_PROVIDER, REQ_KEY, PRD_PROVIDER, PRD_KEY);
        AiModelRegistry registry = context.getBean(AiModelRegistry.class);
        Assertions.assertEquals(Set.of("requirement", "product"), registry.ids());
        Assertions.assertNotNull(registry.get("requirement"), "requirement 实例应可取用");
        Assertions.assertNotNull(registry.get("product"), "product 实例应可取用");
        Assertions.assertNotSame(registry.get("requirement"), registry.get("product"),
                "不同实例必须是独立的 ChatModel");
        Assertions.assertSame(registry.get("product"), registry.first(),
                "字母序第一个实例（product）是默认实例");
    }

    @Test
    @DisplayName("仅配置命名实例时，主 ChatModel 回退为注册表默认实例，ChatClient 仍可用")
    void mainModelFallsBackToRegistryDefault() {
        DefaultApplicationContext context = contextWith(REQ_PROVIDER, REQ_KEY, PRD_PROVIDER, PRD_KEY);
        AiModelRegistry registry = context.getBean(AiModelRegistry.class);
        Assertions.assertSame(registry.first(), context.getBean(ChatModel.class),
                "主 ChatModel 应回退为注册表默认实例");
        Assertions.assertNotNull(context.getBean(ChatClient.class),
                "ChatClient 应基于回退的主 ChatModel 正常装配");
    }

    @Test
    @DisplayName("主模型与命名实例并存：主 ChatModel 独立于注册表实例")
    void mainModelAndRegistryCoexist() {
        DefaultApplicationContext context = contextWith(
                "--summer.ai.provider=deepseek", "--summer.ai.api-key=sk-main",
                PRD_PROVIDER, PRD_KEY);
        AiModelRegistry registry = context.getBean(AiModelRegistry.class);
        Assertions.assertEquals(Set.of("product"), registry.ids(), "仅命名实例进注册表");
        Assertions.assertNotSame(registry.first(), context.getBean(ChatModel.class),
                "主 ChatModel 与注册表实例应相互独立");
    }

    @Test
    @DisplayName("实例缺 api-key 时启动快速失败，提示缺失项")
    void incompleteInstanceFailsFast() {
        Assertions.assertThrows(RuntimeException.class,
                () -> contextWith(REQ_PROVIDER, PRD_PROVIDER, PRD_KEY),
                "provider 齐全但缺 api-key 的实例必须快速失败");
    }

    @Test
    @DisplayName("主模型与命名实例均未配置：启动不失败（懒装配），真正取用时才报错")
    void nothingConfiguredStartsButChatModelFails() {
        DefaultApplicationContext context = contextWith();
        Assertions.assertTrue(context.getBean(AiModelRegistry.class).isEmpty(),
                "未配置时注册表为空但不影响启动");
        RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                () -> context.getBean(ChatModel.class),
                "真正取用 ChatModel 时应报出配置缺失的明确错误");
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        Assertions.assertTrue(messages.toString().contains("summer.ai"),
                "错误信息应指向 summer.ai 配置/动态注册/关闭方式，实际：" + messages);
    }

    @Test
    @DisplayName("按未注册的 id 取模型时抛异常并列出可用 id")
    void unknownIdListsRegisteredIds() {
        DefaultApplicationContext context = contextWith(REQ_PROVIDER, REQ_KEY);
        AiModelRegistry registry = context.getBean(AiModelRegistry.class);
        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> registry.get("nope"));
        Assertions.assertTrue(e.getMessage().contains("requirement"),
                "错误信息应列出已注册的实例 id，实际：" + e.getMessage());
    }

    @Test
    @DisplayName("命名实例的 base-url/model 缺省回退厂商档案默认值")
    void providerDefaultsResolved() {
        Environment env = new Environment(new String[]{PRD_PROVIDER, PRD_KEY});
        NamedAiModel named = NamedAiModel.from("product", env);
        Assertions.assertEquals("https://api.moonshot.cn/v1", named.getBaseUrl());
        Assertions.assertEquals("kimi-k3", named.getModel());
        Assertions.assertTrue(named.isConfigured());
    }
}
