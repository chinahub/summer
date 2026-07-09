package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.model.Provider;
import cn.jiebaba.summer.core.env.Environment;

/**
 * summer.ai.* 配置项绑定：厂商、密钥、模型、base-url、超时、采样参数与可选的弹性策略（重试/限流/熔断）。
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

    private AiProperties(Provider provider, String apiKey, String model, String baseUrl,
                         int timeoutSeconds, double temperature, int maxTokens,
                         int retryMaxAttempts, long retryInitialBackoffMillis, double retryMultiplier,
                         long retryMaxBackoffMillis, double rateLimitPermitsPerSecond,
                         int circuitBreakerFailureThreshold, long circuitBreakerWaitMillis) {
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
        return new AiProperties(provider, apiKey, model, baseUrl, timeoutSeconds, temperature, maxTokens,
                retryMaxAttempts, retryInitialBackoffMillis, retryMultiplier, retryMaxBackoffMillis,
                rateLimitPermitsPerSecond, circuitBreakerFailureThreshold, circuitBreakerWaitMillis);
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
}
