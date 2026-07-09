package cn.jiebaba.summer.ai.chat;

/** system 角色消息，用于设定模型人设与全局指令。 */
public record SystemMessage(String content) implements Message {
    @Override
    public String role() {
        return "system";
    }
}
