package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.model.Provider;
import cn.jiebaba.summer.core.env.Environment;

import java.util.Set;
import java.util.TreeSet;

/**
 * 命名模型实例配置（{@code summer.ai.models.<id>.*}）：多模型注册表的条目，
 * 供"不同环节绑定不同厂商模型"的场景按 id 引用（如 requirement=deepseek、product=kimi）。
 * <p>支持项：provider（厂商档案，决定默认 base-url 与模型名）、api-key、model、base-url、
 * timeout-seconds、temperature、max-tokens。主配置 {@code summer.ai.*} 的弹性策略与工具调用
 * 不作用于命名实例。
 */
public final class NamedAiModel {

    /** 命名实例配置前缀：后跟实例 id 与属性名（id 不支持点号）。 */
    public static final String PREFIX = "summer.ai.models.";

    private final String id;
    private final Provider provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final double temperature;
    private final int maxTokens;

    private NamedAiModel(String id, Provider provider, String apiKey, String model, String baseUrl,
                         int timeoutSeconds, double temperature, int maxTokens) {
        this.id = id;
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /** 枚举环境配置中已声明的实例 id（按字母序）。 */
    public static Set<String> ids(Environment env) {
        Set<String> ids = new TreeSet<>();
        for (String key : env.all().keySet()) {
            if (key.startsWith(PREFIX) && key.length() > PREFIX.length()) {
                String rest = key.substring(PREFIX.length());
                int dot = rest.indexOf('.');
                if (dot > 0) {
                    ids.add(rest.substring(0, dot));
                }
            }
        }
        return ids;
    }

    /** 解析指定 id 的命名实例配置；base-url 与 model 未配置时回退到 provider 档案默认值。 */
    public static NamedAiModel from(String id, Environment env) {
        String base = PREFIX + id + ".";
        Provider provider = Provider.from(env.getProperty(base + "provider"));
        String apiKey = env.getProperty(base + "api-key");
        String model = env.getProperty(base + "model");
        String baseUrl = env.getProperty(base + "base-url");
        if (provider != null) {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = provider.getDefaultBaseUrl();
            }
            if (model == null || model.isBlank()) {
                model = provider.getDefaultModel();
            }
        }
        int timeoutSeconds = env.getProperty(base + "timeout-seconds", Integer.class, 60);
        double temperature = env.getProperty(base + "temperature", Double.class, 0.7);
        int maxTokens = env.getProperty(base + "max-tokens", Integer.class, 2048);
        return new NamedAiModel(id, provider, apiKey, model, baseUrl, timeoutSeconds, temperature, maxTokens);
    }

    /** 是否已完整配置（provider 与 api-key 齐全），不完整时注册表装配将快速失败。 */
    public boolean isConfigured() {
        return provider != null && apiKey != null && !apiKey.isBlank();
    }

    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
}
