package cn.jiebaba.summer.ai.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 进程内 agent 协作任务信封（自研最小信封，字段对齐 agent 协作语义：
 * taskId/conversationId/skill/input/feedback/context）。
 * <p>id 一律字符串：雪花级长整数在 JSON 生态普遍丢精度（见 {@code JsonUtil} 大整数约定）。
 * <p>context 携带键值对形式的上游上下文（可经 {@link #withContext(Map)} 逐级透传与合并），
 * 不可为空（compact 构造中以 {@link Map#of()} 兜底并做不可变拷贝）。
 *
 * @param taskId         任务 id（全局唯一，缺省自动生成 UUID）
 * @param conversationId 会话 id（同一次业务会话的多次协作共享；{@link #begin} 新建时为 null）
 * @param skill          技能名（编排器按此路由到同名执行器，如 "writer"/"reviewer"）
 * @param input          主输入文本
 * @param feedback       上游执行器回传的审查/重做反馈，可空
 * @param context        附带的上下文键值对，非空且不可变
 */
public record AgentTask(
        String taskId,
        String conversationId,
        String skill,
        String input,
        String feedback,
        Map<String, String> context) {

    public AgentTask {
        if (taskId == null || taskId.isBlank()) {
            taskId = UUID.randomUUID().toString();
        }
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    /** 新建任务：自动生成 taskId，conversationId 为 null（可经 {@link #inConversation} 指定）。 */
    public static AgentTask begin(String skill, String input) {
        return new AgentTask(UUID.randomUUID().toString(), null, skill, input, null, Map.of());
    }

    /** 附加重做反馈，返回新任务。 */
    public AgentTask withFeedback(String feedback) {
        return new AgentTask(taskId, conversationId, skill, input, feedback, context);
    }

    /** 合并且透传上游上下文（后者覆盖同名键），返回新任务。 */
    public AgentTask withContext(Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(context);
        merged.putAll(extra);
        return new AgentTask(taskId, conversationId, skill, input, feedback, merged);
    }

    /** 指定会话 id（同会话多次协作共享），返回新任务。 */
    public AgentTask inConversation(String conversationId) {
        return new AgentTask(taskId, conversationId, skill, input, feedback, context);
    }
}
