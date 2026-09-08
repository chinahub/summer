package cn.jiebaba.summer.ai.agent;

/**
 * A2A 委派契约（"委派=持久契约"，断线可恢复；默认落盘 a2a/{requestId}.json，见
 * {@link FileContractStore}）。id 一律字符串。
 *
 * @param requestId   全局唯一请求 id
 * @param messageId   信封消息 id
 * @param conversationId 会话 id
 * @param traceId     链路追踪 id
 * @param direction   OUTBOUND 我方委派出去 / INBOUND 我方接域执行
 * @param peerUrl     OUTBOUND: 对方基地址；INBOUND: 空
 * @param callbackUrl OUTBOUND: 告知对方的回调基地址；INBOUND: 对方告知的回调基地址
 * @param skill       任务技能名
 * @param input       主输入文本
 * @param feedback    重做反馈，可空
 * @param context     委派方附带的上游上下文文本，可空
 * @param status      PENDING/RUNNING/COMPLETED/FAILED
 * @param output      执行输出文本
 * @param evidence    证据回执
 * @param error       错误信息
 * @param createdAt   创建时间（ISO 文本）
 * @param updatedAt   更新时间（ISO 文本）
 */
public record A2aContract(
        String requestId,
        String messageId,
        String conversationId,
        String traceId,
        String direction,
        String peerUrl,
        String callbackUrl,
        String skill,
        String input,
        String feedback,
        String context,
        String status,
        String output,
        String evidence,
        String error,
        String createdAt,
        String updatedAt) {

    public static final String DIR_OUTBOUND = "OUTBOUND";
    public static final String DIR_INBOUND = "INBOUND";

    public static final String ST_PENDING = "PENDING";
    public static final String ST_RUNNING = "RUNNING";
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_FAILED = "FAILED";

    /** 是否仍在途（恢复流程需要盯梢）。 */
    public boolean isActive() {
        return ST_PENDING.equals(status) || ST_RUNNING.equals(status);
    }
}
