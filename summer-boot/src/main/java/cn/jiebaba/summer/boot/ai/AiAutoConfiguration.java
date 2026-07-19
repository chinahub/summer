package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.embedding.EmbeddingModel;
import cn.jiebaba.summer.ai.embedding.openai.OpenAiCompatibleEmbeddingModel;
import cn.jiebaba.summer.ai.logging.AiCallLogger;
import cn.jiebaba.summer.ai.logging.LoggingChatModel;
import cn.jiebaba.summer.ai.memory.ChatMemory;
import cn.jiebaba.summer.ai.memory.MessageWindowChatMemory;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.ai.rag.RagClient;
import cn.jiebaba.summer.ai.rag.RetrievalAugmentationAdvisor;
import cn.jiebaba.summer.ai.rag.VectorStoreRetriever;
import cn.jiebaba.summer.ai.retry.CircuitBreaker;
import cn.jiebaba.summer.ai.retry.RateLimiter;
import cn.jiebaba.summer.ai.retry.ResilientChatModel;
import cn.jiebaba.summer.ai.retry.RetryPolicy;
import cn.jiebaba.summer.ai.tools.ToolCallback;
import cn.jiebaba.summer.ai.tools.ToolCallingChatModel;
import cn.jiebaba.summer.ai.vectorstore.InMemoryVectorStore;
import cn.jiebaba.summer.ai.vectorstore.VectorStore;
import cn.jiebaba.summer.boot.ai.logging.JdbcAiCallLogger;
import cn.jiebaba.summer.boot.ai.vectorstore.JdbcVectorStore;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.Lazy;
import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * summer-ai 自动配置：按 summer.ai.* 装配 ChatModel 与 ChatClient，并可选装配向量化、向量库
 * （内存或 pgvector）、对话记忆与 RAG 检索增强门面。当配置了重试/限流/熔断参数时，ChatModel
 * 会被 ResilientChatModel 包装以增强弹性；启用工具调用（summer.ai.tools.enabled=true）时，
 * 收集上下文中的 ToolCallback bean 并叠加 ToolCallingChatModel 自动执行工具循环（同步与流式均支持）。
 * <p>本类位于 summer-boot，编译期引用 summer-ai（optional），运行期由 SummerApplication
 * 在探测到 summer-ai 在 classpath 后才注册加载；summer-ai 不在则本类永不被加载。
 * <p>EmbeddingModel/VectorStore/ChatMemory/RagClient 以 {@code @Lazy} 注册：仅在注入时才初始化，
 * 未启用对应配置而强行注入时会抛出明确异常，不影响未使用这些能力的应用启动。
 */
@Configuration
public class AiAutoConfiguration {

    @Bean
    public AiProperties aiProperties(Environment env) {
        return AiProperties.from(env);
    }

    /** 装配 ChatModel（OpenAI 兼容，覆盖 DeepSeek/GLM/MiniMax）；未配置 provider 则快速失败；按需叠加弹性策略与工具调用循环。 */
    @Bean
    public ChatModel chatModel(AiProperties aiProperties, ApplicationContext context) {
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
        if (aiProperties.isLoggingEnabled()) {
            model = new LoggingChatModel(model, resolveAiCallLogger(context, aiProperties));
        }
        if (aiProperties.isResilienceEnabled()) {
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
            model = new ResilientChatModel(model, rateLimiter, retryPolicy, circuitBreaker);
        }
        if (aiProperties.isToolsEnabled()) {
            List<ToolCallback> tools = new ArrayList<>(context.getBeansOfType(ToolCallback.class).values());
            if (!tools.isEmpty()) {
                model = new ToolCallingChatModel(model, tools, aiProperties.getToolsMaxIterations());
            }
        }
        return model;
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * 装配向量化模型（OpenAI 兼容 /embeddings 端点），懒加载。
     * base-url 与 api-key 缺省回退到主对话配置，便于 chat 与 embedding 同厂商场景；
     * DeepSeek 等无 embedding 服务的厂商可单独配置 summer.ai.embedding.base-url/api-key 指向其它厂商。
     */
    @Bean
    @Lazy
    public EmbeddingModel embeddingModel(AiProperties aiProperties) {
        if (!aiProperties.isEmbeddingReady()) {
            throw new IllegalStateException(
                    "summer-ai 向量化未启用或未配置：请设置 summer.ai.embedding.enabled=true"
                            + " 与 summer.ai.embedding.model（必要时补充 base-url/api-key）。");
        }
        return new OpenAiCompatibleEmbeddingModel(
                aiProperties.resolveEmbeddingBaseUrl(),
                aiProperties.resolveEmbeddingApiKey(),
                aiProperties.getEmbeddingModel(),
                Duration.ofSeconds(aiProperties.getTimeoutSeconds()));
    }

    /**
     * 装配向量库（懒加载）：vectorstore.type=memory 用内存实现；type=pgvector 用 PostgreSQL+pgvector 持久化实现，
     * 后者复用 summer-data 的 SqlExecutor 执行 SQL（仅 pgvector 模式才要求 SqlExecutor Bean，memory 模式不依赖数据库）。
     */
    @Bean
    @Lazy
    public VectorStore vectorStore(AiProperties aiProperties, EmbeddingModel embeddingModel, ApplicationContext context) {
        if (aiProperties.isVectorStoreMemory()) {
            return new InMemoryVectorStore(embeddingModel);
        }
        if (aiProperties.isVectorStorePgVector()) {
            SqlExecutor sqlExecutor = resolveSqlExecutor(context);
            return new JdbcVectorStore(sqlExecutor, embeddingModel, aiProperties.getVectorStoreTable(),
                    aiProperties.getVectorStoreDimensions(),
                    aiProperties.isVectorStoreCreateExtension(),
                    aiProperties.isVectorStoreCreateIndex());
        }
        throw new IllegalStateException(
                "summer-ai 向量库未启用：请设置 summer.ai.vectorstore.type=memory 或 pgvector。");
    }

    /** 装配对话记忆（懒加载），基于消息窗口保留最近若干轮并始终保留首条 system 人设。 */
    @Bean
    @Lazy
    public ChatMemory chatMemory(AiProperties aiProperties) {
        if (!aiProperties.isMemoryEnabled()) {
            throw new IllegalStateException(
                    "summer-ai 对话记忆未启用：请设置 summer.ai.memory.enabled=true。");
        }
        return new MessageWindowChatMemory(aiProperties.getMemoryMaxMessages());
    }

    /**
     * 装配 RAG 检索增强门面（懒加载）：组合 ChatClient 与基于向量库的检索增强顾问，
     * 一步完成「检索资料 + 增强提问 + 调用模型」。要求 rag.enabled=true 且向量库与向量化均已就绪。
     */
    @Bean
    @Lazy
    public RagClient ragClient(AiProperties aiProperties, ChatClient chatClient, VectorStore vectorStore) {
        if (!aiProperties.isRagReady()) {
            throw new IllegalStateException(
                    "summer-ai RAG 未就绪：请设置 summer.ai.rag.enabled=true，"
                            + "并启用 summer.ai.vectorstore.type=memory|pgvector 与 summer.ai.embedding.*。");
        }
        VectorStoreRetriever retriever = new VectorStoreRetriever(
                vectorStore, aiProperties.getRagTopK(), aiProperties.getRagSimilarityThreshold());
        String instruction = aiProperties.getRagInstruction();
        RetrievalAugmentationAdvisor advisor = (instruction == null || instruction.isBlank())
                ? new RetrievalAugmentationAdvisor(retriever)
                : new RetrievalAugmentationAdvisor(retriever, instruction);
        return new RagClient(chatClient, advisor);
    }

    /** 从上下文查找 SqlExecutor；缺失时抛出明确异常，提示配置 summer.datasource.*。 */
    private static SqlExecutor resolveSqlExecutor(ApplicationContext context) {
        try {
            return context.getBean(SqlExecutor.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "pgvector 向量库需要 SqlExecutor：请配置 summer.datasource.* 以启用 summer-data 数据层。", e);
        }
    }

    /** 解析 AiCallLogger：优先用上下文自定义 bean，否则以 SqlExecutor 构造 JdbcAiCallLogger。 */
    private static AiCallLogger resolveAiCallLogger(ApplicationContext context, AiProperties aiProperties) {
        try {
            return context.getBean(AiCallLogger.class);
        } catch (RuntimeException ignored) {
            // 无自定义 AiCallLogger bean，回退到 JDBC 实现
        }
        return new JdbcAiCallLogger(resolveSqlExecutor(context), aiProperties.getLoggingTable());
    }
}
