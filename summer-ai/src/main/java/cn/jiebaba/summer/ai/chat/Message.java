package cn.jiebaba.summer.ai.chat;

import cn.jiebaba.summer.ai.chat.content.ContentPart;

import java.util.List;

/**
 * 对话消息抽象，统一 role 与 content 两个字段，兼容 OpenAI 协议消息格式。
 * 多模态消息通过 parts() 返回内容片段列表；assistant 消息可通过 toolCalls() 携带工具调用。
 */
public interface Message {

    /** 消息角色：system/user/assistant/tool。 */
    String role();

    /** 消息文本内容（多模态消息取所有文本片段拼接结果）。 */
    String content();

    /** 多模态内容片段，默认空列表（纯文本消息返回空，由 content() 提供文本）。 */
    default List<ContentPart> parts() {
        return List.of();
    }

    /** 助手消息携带的工具调用列表，默认空列表。 */
    default List<ToolCall> toolCalls() {
        return List.of();
    }

    static SystemMessage system(String content) {
        return new SystemMessage(content);
    }

    static UserMessage user(String content) {
        return new UserMessage(content);
    }

    static AssistantMessage assistant(String content) {
        return new AssistantMessage(content);
    }

    /** 构造工具结果消息，role 为 tool。 */
    static ToolMessage tool(String toolCallId, String content) {
        return new ToolMessage(toolCallId, content);
    }
}
