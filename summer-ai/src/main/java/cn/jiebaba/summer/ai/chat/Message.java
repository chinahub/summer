package cn.jiebaba.summer.ai.chat;

/** 对话消息抽象，统一 role 与 content 两个字段，兼容 OpenAI 协议消息格式。 */
public interface Message {

    /** 消息角色：system/user/assistant/tool。 */
    String role();

    /** 消息文本内容。 */
    String content();

    static SystemMessage system(String content) {
        return new SystemMessage(content);
    }

    static UserMessage user(String content) {
        return new UserMessage(content);
    }

    static AssistantMessage assistant(String content) {
        return new AssistantMessage(content);
    }
}
