package cn.jiebaba.summer.ai.model;

/**
 * 大模型厂商档案，内置默认 base-url 与默认模型名（均可被配置覆盖）。
 * 默认模型名为当前稳定版本示例；切换 DeepSeek-V4、GLM-5.4、MiniMax-M3 等
 * 新版本时通过 summer.ai.model 配置指定即可，无需改代码。
 * 含国内厂商（DeepSeek/GLM/MiniMax/Kimi）与订阅制聚合服务 OpenCode Go
 * （OpenAI 兼容端点，模型 ID 如 kimi-k3、glm-5.3-flash、mimo-v2.6-pro）。
 */
public enum Provider {

    DEEPSEEK("https://api.deepseek.com", "deepseek-chat"),
    GLM("https://open.bigmodel.cn/api/paas/v4", "glm-5.3-flash"),
    MINIMAX("https://api.minimax.chat/v1", "MiniMax-Text-01"),
    KIMI("https://api.moonshot.cn/v1", "kimi-k3"),
    /** OpenCode Go 订阅制聚合服务（opencode.ai），仅 chat/completions 系模型可用，api-key 为 OPENCODE_API_KEY。 */
    OPENCODE_GO("https://opencode.ai/zen/go/v1", "glm-5.3-flash");

    private final String defaultBaseUrl;
    private final String defaultModel;

    Provider(String defaultBaseUrl, String defaultModel) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * 按名称解析厂商，不区分大小写，忽略连字符/下划线分隔符（如 opencode-go、opencode_go、
     * OPENCODE_GO 均可匹配）；未匹配返回 null。
     */
    public static Provider from(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = normalize(name);
        for (Provider p : values()) {
            if (normalize(p.name()).equalsIgnoreCase(normalized)) {
                return p;
            }
        }
        return null;
    }

    /** 归一化名称：去首尾空白并移除连字符与下划线（大小写由调用方忽略）。 */
    private static String normalize(String name) {
        return name.trim().replace("-", "").replace("_", "");
    }
}
