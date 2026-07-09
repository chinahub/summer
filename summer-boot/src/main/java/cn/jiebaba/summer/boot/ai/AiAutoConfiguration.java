package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.ai.retry.CircuitBreaker;
import cn.jiebaba.summer.ai.retry.RateLimiter;
import cn.jiebaba.summer.ai.retry.ResilientChatModel;
import cn.jiebaba.summer.ai.retry.RetryPolicy;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;

import java.time.Duration;

/**
 * summer-ai 自动配置：按 summer.ai.* 装配 ChatModel 与 ChatClient。
 * 当配置了重试/限流/熔断参数时，ChatModel 会被 ResilientChatModel 包装以增强弹性。
 * 本类位于 summer-boot，编译期引用 summer-ai（optional），运行期由 SummerApplication
 * 在探测到 summer-ai 在 classpath 后才注册加载；summer-ai 不在则本类永不被加载。
 */
@Configuration
public class AiAutoConfiguration {

    @Bean
    public AiProperties aiProperties(Environment env) {
        return AiProperties.from(env);
    }

    /** 装配 ChatModel（OpenAI 兼容，覆盖 DeepSeek/GLM/MiniMax）；未配置 provider 则快速失败；按需叠加弹性策略。 */
    @Bean
    public ChatModel chatModel(AiProperties aiProperties) {
        if (!aiProperties.isConfigured()) {
            throw new IllegalStateException(
                    "summer-ai 已在 classpath 但未正确配置：请设置 summer.ai.provider"
                            + "(deepseek|glm|minimax) 与 summer.ai.api-key。");
        }
        ChatModel model = new OpenAiCompatibleChatModel(
                aiProperties.getBaseUrl(),
                aiProperties.getApiKey(),
                aiProperties.getModel(),
                Duration.ofSeconds(aiProperties.getTimeoutSeconds()),
                aiProperties.getTemperature(),
                aiProperties.getMaxTokens());
        if (!aiProperties.isResilienceEnabled()) {
            return model;
        }
        RateLimiter rateLimiter = aiProperties.getRateLimitPermitsPerSecond() > 0
                ? new RateLimiter(aiProperties.getRateLimitPermitsPerSecond()) : null;
        RetryPolicy retryPolicy = aiProperties.getRetryMaxAttempts() > 1
                ? RetryPolicy.builder()
                    .maxAttempts(aiProperties.getRetryMaxAttempts())
                    .initialBackoff(Duration.ofMillis(aiProperties.getRetryInitialBackoffMillis()))
                    .multiplier(aiProperties.getRetryMultiplier())
                    .maxBackoff(Duration.ofMillis(aiProperties.getRetryMaxBackoffMillis()))
                    .build()
                : null;
        CircuitBreaker circuitBreaker = aiProperties.getCircuitBreakerFailureThreshold() > 0
                ? new CircuitBreaker(aiProperties.getCircuitBreakerFailureThreshold(),
                    Duration.ofMillis(aiProperties.getCircuitBreakerWaitMillis()))
                : null;
        return new ResilientChatModel(model, rateLimiter, retryPolicy, circuitBreaker);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
