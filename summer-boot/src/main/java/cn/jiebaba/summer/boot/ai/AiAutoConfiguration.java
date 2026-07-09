package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;

import java.time.Duration;

/**
 * summer-ai 自动配置：按 summer.ai.* 装配 ChatModel 与 ChatClient。
 * 本类位于 summer-boot，编译期引用 summer-ai（optional），运行期由 SummerApplication
 * 在探测到 summer-ai 在 classpath 后才注册加载；summer-ai 不在则本类永不被加载。
 */
@Configuration
public class AiAutoConfiguration {

    @Bean
    public AiProperties aiProperties(Environment env) {
        return AiProperties.from(env);
    }

    /** 装配 OpenAI 兼容 ChatModel（覆盖 DeepSeek/GLM/MiniMax）；未配置 provider 则快速失败。 */
    @Bean
    public ChatModel chatModel(AiProperties aiProperties) {
        if (!aiProperties.isConfigured()) {
            throw new IllegalStateException(
                    "summer-ai 已在 classpath 但未正确配置：请设置 summer.ai.provider"
                            + "(deepseek|glm|minimax) 与 summer.ai.api-key。");
        }
        return new OpenAiCompatibleChatModel(
                aiProperties.getBaseUrl(),
                aiProperties.getApiKey(),
                aiProperties.getModel(),
                Duration.ofSeconds(aiProperties.getTimeoutSeconds()),
                aiProperties.getTemperature(),
                aiProperties.getMaxTokens());
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
