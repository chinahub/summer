package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.chat.ChatModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 模型注册表：按实例 id（{@code summer.ai.models.<id>}）持有多个命名 ChatModel，
 * 供编排/业务代码按阶段引用不同厂商模型。保持插入序（配置枚举按字母序），
 * {@link #first()} 返回默认实例（第一个）。
 */
public final class AiModelRegistry {

    private final Map<String, ChatModel> models;

    public AiModelRegistry(Map<String, ChatModel> models) {
        this.models = new LinkedHashMap<>(models);
    }

    /** 按 id 取模型；不存在时抛出异常并列出已注册 id，便于定位配置错误。 */
    public ChatModel get(String id) {
        ChatModel model = models.get(id);
        if (model == null) {
            throw new IllegalStateException(
                    "未找到模型实例 \"" + id + "\"，已注册的实例：" + (models.isEmpty() ? "(无)" : models.keySet()));
        }
        return model;
    }

    /** 默认模型实例（第一个注册的）；注册表为空时返回 null。 */
    public ChatModel first() {
        return models.isEmpty() ? null : models.values().iterator().next();
    }

    /** 已注册的实例 id（保序）。 */
    public Set<String> ids() {
        return models.keySet();
    }

    /** 注册表是否为空（未配置任何命名实例）。 */
    public boolean isEmpty() {
        return models.isEmpty();
    }
}
