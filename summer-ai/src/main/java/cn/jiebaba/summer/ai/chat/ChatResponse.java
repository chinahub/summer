package cn.jiebaba.summer.ai.chat;

/**
 * 模型响应：助手内容、思维链（reasoningContent，国内思考模型特有字段）、
 * 结束原因与元数据。流式调用时每个片段仅含增量 content/reasoningContent。
 */
public record ChatResponse(
        String content,
        String reasoningContent,
        String finishReason,
        ChatResponseMetadata metadata) {
}
