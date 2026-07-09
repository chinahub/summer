package cn.jiebaba.summer.ai.chat;

import java.util.List;

/**
 * 模型响应：助手内容、思维链（reasoningContent，国内思考模型特有字段）、
 * 结束原因、工具调用列表与元数据。流式调用时每个片段仅含增量 content/reasoningContent。
 */
public record ChatResponse(
        String content,
        String reasoningContent,
        String finishReason,
        List<ToolCall> toolCalls,
        ChatResponseMetadata metadata) {

    public ChatResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 兼容旧构造：不含工具调用的响应。 */
    public ChatResponse(String content, String reasoningContent, String finishReason, ChatResponseMetadata metadata) {
        this(content, reasoningContent, finishReason, List.of(), metadata);
    }
}
