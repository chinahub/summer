package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.model.Provider;
import cn.jiebaba.summer.core.env.Environment;

/**
 * summer.ai.* 配置项绑定：厂商、密钥、模型、base-url、超时、采样参数与可选的弹性策略（重试/限流/熔断），
 * 以及向量化、向量库（内存或 pgvector）、对话记忆与 RAG 检索增强的可选装配参数。
 */
public class AiProperties {

    private final Provider provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final double temperature;
    private final int maxTokens;
    private final int retryMaxAttempts;
    private final long retryInitialBackoffMillis;
    private final double retryMultiplier;
    private final long retryMaxBackoffMillis;
    private final double rateLimitPermitsPerSecond;
    private final int circuitBreakerFailureThreshold;
    private final long circuitBreakerWaitMillis;
    private final boolean embeddingEnabled;
    private final String embeddingModel;
    private final String embeddingBaseUrl;
    private final String embeddingApiKey;
    private final String vectorStoreType;
    private final String vectorStoreTable;
    private final int vectorStoreDimensions;
    private final boolean vectorStoreCreateExtension;
    private final boolean vectorStoreCreateIndex;
    private final boolean memoryEnabled;
    private final int memoryMaxMessages;
    private final boolean ragEnabled;
    private final int ragTopK;
    private final double ragSimilarityThreshold;
    private final String ragInstruction;
    private final boolean toolsEnabled;
    private final int toolsMaxIterations;
    private final boolean loggingEnabled;
    private final String loggingTable;

    private AiProperties(Provider provider, String apiKey, String model, String baseUrl,
                         int timeoutSeconds, double temperature, int maxTokens,
                         int retryMaxAttempts, long retryInitialBackoffMillis, double retryMultiplier,
                         long retryMaxBackoffMillis, double rateLimitPermitsPerSecond,
                         int circuitBreakerFailureThreshold, long circuitBreakerWaitMillis,
                         boolean embeddingEnabled, String embeddingModel, String embeddingBaseUrl, String embeddingApiKey,
                         String vectorStoreType, String vectorStoreTable, int vectorStoreDimensions,
                         boolean vectorStoreCreateExtension, boolean vectorStoreCreateIndex,
                         boolean memoryEnabled, int memoryMaxMessages,
                         boolean ragEnabled, int ragTopK, double ragSimilarityThreshold, String ragInstruction,
                         boolean toolsEnabled, int toolsMaxIterations,
                         boolean loggingEnabled, String loggingTable) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryInitialBackoffMillis = retryInitialBackoffMillis;
        this.retryMultiplier = retryMultiplier;
        this.retryMaxBackoffMillis = retryMaxBackoffMillis;
        this.rateLimitPermitsPerSecond = rateLimitPermitsPerSecond;
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
        this.circuitBreakerWaitMillis = circuitBreakerWaitMillis;
        this.embeddingEnabled = embeddingEnabled;
        this.embeddingModel = embeddingModel;
        this.embeddingBaseUrl = embeddingBaseUrl;
        this.embeddingApiKey = embeddingApiKey;
        this.vectorStoreType = vectorStoreType;
        this.vectorStoreTable = vectorStoreTable;
        this.vectorStoreDimensions = vectorStoreDimensions;
        this.vectorStoreCreateExtension = vectorStoreCreateExtension;
        this.vectorStoreCreateIndex = vectorStoreCreateIndex;
        this.memoryEnabled = memoryEnabled;
        this.memoryMaxMessages = memoryMaxMessages;
        this.ragEnabled = ragEnabled;
        this.ragTopK = ragTopK;
        this.ragSimilarityThreshold = ragSimilarityThreshold;
        this.ragInstruction = ragInstruction;
        this.toolsEnabled = toolsEnabled;
        this.toolsMaxIterations = toolsMaxIterations;
        this.loggingEnabled = loggingEnabled;
        this.loggingTable = loggingTable;
    }

    /** 从环境配置解析 AiProperties；未配置 provider 时返回未激活实例（provider 为 null）。 */
    public static AiProperties from(Environment env) {
        Provider provider = Provider.from(env.getProperty("summer.ai.provider"));
        String apiKey = env.getProperty("summer.ai.api-key");
        String model = env.getProperty("summer.ai.model");
        String baseUrl = env.getProperty("summer.ai.base-url");
        if (provider != null) {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = provider.getDefaultBaseUrl();
            }
            if (model == null || model.isBlank()) {
                model = provider.getDefaultModel();
            }
        }
        int timeoutSeconds = env.getProperty("summer.ai.timeout-seconds", Integer.class, 60);
        double temperature = env.getProperty("summer.ai.temperature", Double.class, 0.7);
        int maxTokens = env.getProperty("summer.ai.max-tokens", Integer.class, 2048);
        int retryMaxAttempts = env.getProperty("summer.ai.retry.max-attempts", Integer.class, 1);
        long retryInitialBackoffMillis = env.getProperty("summer.ai.retry.initial-backoff-millis", Long.class, 500L);
        double retryMultiplier = env.getProperty("summer.ai.retry.multiplier", Double.class, 2.0);
        long retryMaxBackoffMillis = env.getProperty("summer.ai.retry.max-backoff-millis", Long.class, 20000L);
        double rateLimitPermitsPerSecond = env.getProperty("summer.ai.rate-limit.permits-per-second", Double.class, 0.0);
        int circuitBreakerFailureThreshold = env.getProperty("summer.ai.circuit-breaker.failure-threshold", Integer.class, 0);
        long circuitBreakerWaitMillis = env.getProperty("summer.ai.circuit-breaker.wait-millis", Long.class, 30000L);
        boolean embeddingEnabled = env.getProperty("summer.ai.embedding.enabled", Boolean.class, false);
        String embeddingModel = env.getProperty("summer.ai.embedding.model");
        String embeddingBaseUrl = env.getProperty("summer.ai.embedding.base-url");
        String embeddingApiKey = env.getProperty("summer.ai.embedding.api-key");
        String vectorStoreType = env.getProperty("summer.ai.vectorstore.type", String.class, "none");
        String vectorStoreTable = env.getProperty("summer.ai.vectorstore.table", String.class, "summer_ai_vectors");
        int vectorStoreDimensions = env.getProperty("summer.ai.vectorstore.dimensions", Integer.class, 0);
        boolean vectorStoreCreateExtension = env.getProperty("summer.ai.vectorstore.create-extension", Boolean.class, true);
        boolean vectorStoreCreateIndex = env.getProperty("summer.ai.vectorstore.create-index", Boolean.class, true);
        boolean memoryEnabled = env.getProperty("summer.ai.memory.enabled", Boolean.class, false);
        int memoryMaxMessages = env.getProperty("summer.ai.memory.max-messages", Integer.class, 20);
        boolean ragEnabled = env.getProperty("summer.ai.rag.enabled", Boolean.class, false);
        int ragTopK = env.getProperty("summer.ai.rag.top-k", Integer.class, 4);
        double ragSimilarityThreshold = env.getProperty("summer.ai.rag.similarity-threshold", Double.class, 0.0);
        String ragInstruction = env.getProperty("summer.ai.rag.instruction");
        boolean toolsEnabled = env.getProperty("summer.ai.tools.enabled", Boolean.class, false);
        int toolsMaxIterations = env.getProperty("summer.ai.tools.max-iterations", Integer.class, 10);
        boolean loggingEnabled = env.getProperty("summer.ai.logging.enabled", Boolean.class, false);
        String loggingTable = env.getProperty("summer.ai.logging.table", String.class, "ai_call_log");
        return new AiProperties(provider, apiKey, model, baseUrl, timeoutSeconds, temperature, maxTokens,
                retryMaxAttempts, retryInitialBackoffMillis, retryMultiplier, retryMaxBackoffMillis,
                rateLimitPermitsPerSecond, circuitBreakerFailureThreshold, circuitBreakerWaitMillis,
                embeddingEnabled, embeddingModel, embeddingBaseUrl, embeddingApiKey,
                vectorStoreType, vectorStoreTable, vectorStoreDimensions,
                vectorStoreCreateExtension, vectorStoreCreateIndex,
                memoryEnabled, memoryMaxMessages,
                ragEnabled, ragTopK, ragSimilarityThreshold, ragInstruction,
                toolsEnabled, toolsMaxIterations,
                loggingEnabled, loggingTable);
    }

    /** 是否已正确配置（provider 与 api-key 齐全）。 */
    public boolean isConfigured() {
        return provider != null && apiKey != null && !apiKey.isBlank();
    }

    /** 是否启用任一弹性策略（重试/限流/熔断）。 */
    public boolean isResilienceEnabled() {
        return retryMaxAttempts > 1
                || rateLimitPermitsPerSecond > 0
                || circuitBreakerFailureThreshold > 0;
    }

    /** 向量化是否就绪：embedding.enabled=true 且指定了 embedding.model 并可解析到 api-key。 */
    public boolean isEmbeddingReady() {
        return embeddingEnabled
                && embeddingModel != null && !embeddingModel.isBlank()
                && resolveEmbeddingApiKey() != null && !resolveEmbeddingApiKey().isBlank();
    }

    /** 向量化实际 base-url：优先 embedding.base-url，未配置回退到主 base-url。 */
    public String resolveEmbeddingBaseUrl() {
        return (embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()) ? embeddingBaseUrl : baseUrl;
    }

    /** 向量化实际 api-key：优先 embedding.api-key，未配置回退到主 api-key。 */
    public String resolveEmbeddingApiKey() {
        return (embeddingApiKey != null && !embeddingApiKey.isBlank()) ? embeddingApiKey : apiKey;
    }

    /** 是否启用向量库（任意类型：memory 或 pgvector）。 */
    public boolean isVectorStoreEnabled() {
        return isVectorStoreMemory() || isVectorStorePgVector();
    }

    /** 是否启用内存向量库：vectorstore.type=memory。 */
    public boolean isVectorStoreMemory() {
        return "memory".equalsIgnoreCase(vectorStoreType);
    }

    /** 是否启用 pgvector 持久化向量库：vectorstore.type=pgvector。 */
    public boolean isVectorStorePgVector() {
        return "pgvector".equalsIgnoreCase(vectorStoreType);
    }

    /** 是否启用对话记忆。 */
    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    /** RAG 是否就绪：rag.enabled=true 且向量库与向量化均已就绪。 */
    public boolean isRagReady() {
        return ragEnabled && isVectorStoreEnabled() && isEmbeddingReady();
    }

    public Provider getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public long getRetryInitialBackoffMillis() { return retryInitialBackoffMillis; }
    public double getRetryMultiplier() { return retryMultiplier; }
    public long getRetryMaxBackoffMillis() { return retryMaxBackoffMillis; }
    public double getRateLimitPermitsPerSecond() { return rateLimitPermitsPerSecond; }
    public int getCircuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public long getCircuitBreakerWaitMillis() { return circuitBreakerWaitMillis; }
    public String getEmbeddingModel() { return embeddingModel; }
    public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public String getVectorStoreType() { return vectorStoreType; }
    public String getVectorStoreTable() { return vectorStoreTable; }
    public int getVectorStoreDimensions() { return vectorStoreDimensions; }
    public boolean isVectorStoreCreateExtension() { return vectorStoreCreateExtension; }
    public boolean isVectorStoreCreateIndex() { return vectorStoreCreateIndex; }
    public int getMemoryMaxMessages() { return memoryMaxMessages; }
    public int getRagTopK() { return ragTopK; }
    public double getRagSimilarityThreshold() { return ragSimilarityThreshold; }
    public String getRagInstruction() { return ragInstruction; }
    public boolean isToolsEnabled() { return toolsEnabled; }
    public int getToolsMaxIterations() { return toolsMaxIterations; }
    public boolean isLoggingEnabled() { return loggingEnabled; }
    public String getLoggingTable() { return loggingTable; }
}
