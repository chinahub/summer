package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.chat.ChatModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 模型注册表：按实例 id 持有多个命名 ChatModel，供编排/业务代码按阶段或按数字员工实例
 * 引用不同厂商模型。配置来源两条：
 * <ul>
 *   <li>静态：{@code summer.ai.models.<id>.*} 命名实例（启动时装配）；</li>
 *   <li>动态：{@link #register(String, ChatModel)} 运行期注册（如按员工实例热绑定模型），
 *       未命中回落可用 {@link #resolve(String...)} 表达"实例未绑定 → 角色默认"。</li>
 * </ul>
 * 保持插入序（配置枚举按字母序，动态注册按注册序追加），{@link #first()} 返回默认实例（第一个）。
 * 写时复制快照：读路径无锁，注册/注销为低频操作。
 */
public final class AiModelRegistry {

    /** 模型快照（写时复制，读路径无锁） */
    private volatile Map<String, ChatModel> models;

    public AiModelRegistry(Map<String, ChatModel> models) {
        this.models = Collections.unmodifiableMap(new LinkedHashMap<>(models));
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

    /** 按 id 取模型；不存在时返回 null（调用方自行决定回落策略）。 */
    public ChatModel getOptional(String id) {
        return models.get(id);
    }

    /**
     * 按 id 链依次解析，返回第一个命中者（多级回落：员工实例 → 角色默认 → 全局默认等）；
     * 全部未命中时抛出异常并列出已尝试 id 与已注册实例。
     */
    public ChatModel resolve(String... ids) {
        for (String id : ids) {
            if (id == null) continue;
            ChatModel model = models.get(id);
            if (model != null) return model;
        }
        StringBuilder sb = new StringBuilder("模型回落链全部未命中（已尝试 id：");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(" → ");
            sb.append(ids[i]);
        }
        sb.append("），已注册的实例：").append(models.isEmpty() ? "(无)" : models.keySet());
        throw new IllegalStateException(sb.toString());
    }

    /** 运行期注册（或覆盖）命名模型实例，立即对后续解析生效。 */
    public synchronized void register(String id, ChatModel model) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("模型实例 id 不能为空");
        if (model == null) throw new IllegalArgumentException("模型实例不能为空: " + id);
        Map<String, ChatModel> next = new LinkedHashMap<>(models);
        next.put(id, model);
        models = Collections.unmodifiableMap(next);
    }

    /** 运行期注销命名模型实例；返回是否确有实例被移除。 */
    public synchronized boolean unregister(String id) {
        Map<String, ChatModel> current = models;
        if (!current.containsKey(id)) return false;
        Map<String, ChatModel> next = new LinkedHashMap<>(current);
        next.remove(id);
        models = Collections.unmodifiableMap(next);
        return true;
    }

    /** 默认模型实例（第一个注册的）；注册表为空时返回 null。 */
    public ChatModel first() {
        return models.isEmpty() ? null : models.values().iterator().next();
    }

    /** 已注册的实例 id（保序）。 */
    public Set<String> ids() {
        return models.keySet();
    }

    /** 注册表是否为空（未配置也未动态注册任何命名实例）。 */
    public boolean isEmpty() {
        return models.isEmpty();
    }
}
