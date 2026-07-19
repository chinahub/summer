package cn.jiebaba.summer.ai.logging;

/**
 * 单次大模型调用日志记录：模型名、token 用量、耗时、成功与否、错误信息与提问摘要。
 * 由 {@link LoggingChatModel} 在每次 call/stream 完成后采集，交由 {@link AiCallLogger} 落库或输出。
 * token 字段为 Long 可空：调用失败或流式未携带 usage 时为 null。
 */
public record AiCallLog(
        String model,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        long latencyMillis,
        boolean success,
        String errorMessage,
        String querySummary) {
}
