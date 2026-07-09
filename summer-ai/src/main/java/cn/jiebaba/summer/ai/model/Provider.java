package cn.jiebaba.summer.ai.model;

/**
 * 国内大模型厂商档案，内置默认 base-url 与默认模型名（均可被配置覆盖）。
 * 默认模型名为当前稳定版本示例；切换 DeepSeek-V4、GLM-5.2、MiniMax-M3 等
 * 新版本时通过 summer.ai.model 配置指定即可，无需改代码。
 */
public enum Provider {

    DEEPSEEK("https://api.deepseek.com", "deepseek-chat"),
    GLM("https://open.bigmodel.cn/api/paas/v4", "glm-4"),
    MINIMAX("https://api.minimax.chat/v1", "MiniMax-Text-01");

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

    /** 按名称解析厂商，不区分大小写；未匹配返回 null。 */
    public static Provider from(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Provider p : values()) {
            if (p.name().equalsIgnoreCase(name.trim())) {
                return p;
            }
        }
        return null;
    }
}
