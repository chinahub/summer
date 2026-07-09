package cn.jiebaba.summer.ai.chat;

/** assistant 角色消息，承载模型历史回复。 */
public record AssistantMessage(String content) implements Message {
    @Override
    public String role() {
        return "assistant";
    }
}
