package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.model.Provider;
import cn.jiebaba.summer.core.env.Environment;

/** summer.ai.* 配置项绑定：厂商、密钥、模型、base-url、超时与采样参数。 */
public class AiProperties {

    private final Provider provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final double temperature;
    private final int maxTokens;

    private AiProperties(Provider provider, String apiKey, String model, String baseUrl,
                         int timeoutSeconds, double temperature, int maxTokens) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
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
        return new AiProperties(provider, apiKey, model, baseUrl, timeoutSeconds, temperature, maxTokens);
    }

    /** 是否已正确配置（provider 与 api-key 齐全）。 */
    public boolean isConfigured() {
        return provider != null && apiKey != null && !apiKey.isBlank();
    }

    public Provider getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
}
