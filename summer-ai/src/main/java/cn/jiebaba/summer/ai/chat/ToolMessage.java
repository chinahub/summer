package cn.jiebaba.summer.ai.chat;

/**
 * tool 角色消息：承载工具调用的返回结果，
 * 通过 toolCallId 与 assistant 消息中的 tool_calls 一一对应。
 */
public record ToolMessage(String toolCallId, String content) implements Message {

    @Override
    public String role() {
        return "tool";
    }
}
