package cn.jiebaba.summer.ai.chat;

import java.util.List;

/**
 * assistant 角色消息，承载回复文本与可选的工具调用。
 * 回放历史对话时，携带工具调用的助手消息需一并传入 tool_calls 字段。
 */
public record AssistantMessage(String content, List<ToolCall> toolCalls) implements Message {

    public AssistantMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public AssistantMessage(String content) {
        this(content, List.of());
    }

    @Override
    public String role() {
        return "assistant";
    }

    @Override
    public List<ToolCall> toolCalls() {
        return toolCalls;
    }
}
