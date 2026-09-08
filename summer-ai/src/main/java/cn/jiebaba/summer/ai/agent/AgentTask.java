package cn.jiebaba.summer.ai.agent;

import java.util.UUID;

/**
 * A2A 委派任务（自研最小信封，字段对齐 Google A2A 关键语义：messageId/conversationId/traceId）。
 * <p>id 一律字符串：雪花级长整数在 JSON 生态普遍丢精度（见 {@code JsonUtil} 大整数约定）。
 *
 * @param requestId   全局唯一请求 id（契约文件名、幂等键）
 * @param messageId   信封消息 id
 * @param conversationId 会话 id（同一次业务会话的多次委派共享，形如 "conv-..."）
 * @param traceId     链路追踪 id
 * @param skill       技能名（委派方业务语义，如 "review"/"translate"）
 * @param input       主输入文本
 * @param feedback    重做反馈，可空
 * @param context     委派方附带的上游上下文文本，可空
 * @param callbackUrl 对方回调本方的基地址，可空（空 = 对方回调失败时本方凭 requestId 轮询取回）
 */
public record AgentTask(
        String requestId,
        String messageId,
        String conversationId,
        String traceId,
        String skill,
        String input,
        String feedback,
        String context,
        String callbackUrl) {

    /** 新建任务：自动生成 requestId/messageId/conversationId/traceId。 */
    public static AgentTask begin(String skill, String input) {
        String requestId = UUID.randomUUID().toString();
        return new AgentTask(requestId, UUID.randomUUID().toString(), "conv-" + requestId,
                UUID.randomUUID().toString(), skill, input, null, null, null);
    }

    public AgentTask withFeedback(String feedback) {
        return new AgentTask(requestId, messageId, conversationId, traceId, skill, input, feedback, context, callbackUrl);
    }

    public AgentTask withContext(String context) {
        return new AgentTask(requestId, messageId, conversationId, traceId, skill, input, feedback, context, callbackUrl);
    }

    public AgentTask withCallbackUrl(String callbackUrl) {
        return new AgentTask(requestId, messageId, conversationId, traceId, skill, input, feedback, context, callbackUrl);
    }
}
