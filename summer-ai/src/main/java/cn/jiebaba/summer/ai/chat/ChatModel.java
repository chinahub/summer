package cn.jiebaba.summer.ai.chat;

import java.util.stream.Stream;

/** provider 无关的对话模型接口，提供同步与流式两种调用方式。 */
public interface ChatModel {

    /** 同步调用，返回完整响应。 */
    ChatResponse call(Prompt prompt);

    /** 流式调用，逐 token 返回响应片段。 */
    Stream<ChatResponse> stream(Prompt prompt);
}
