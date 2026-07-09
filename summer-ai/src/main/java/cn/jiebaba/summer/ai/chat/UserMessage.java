package cn.jiebaba.summer.ai.chat;

/** user 角色消息，承载用户输入。 */
public record UserMessage(String content) implements Message {
    @Override
    public String role() {
        return "user";
    }
}
