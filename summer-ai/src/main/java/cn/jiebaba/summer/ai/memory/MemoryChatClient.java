package cn.jiebaba.summer.ai.memory;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Message;

import java.util.List;
import java.util.stream.Stream;

/**
 * 带记忆的会话客户端：在 ChatClient 之上按 conversationId 维护多轮上下文。
 * 调用前载入历史，调用后回存用户与助手消息，调用方无需手动拼接历史。
 */
public class MemoryChatClient {

    private final ChatClient chatClient;
    private final ChatMemory memory;
    private final String conversationId;

    public MemoryChatClient(ChatClient chatClient, ChatMemory memory, String conversationId) {
        this.chatClient = chatClient;
        this.memory = memory;
        this.conversationId = conversationId;
    }

    /** 同步提问：载入历史 -> 追加用户消息 -> 调用 -> 回存助手回复并返回。 */
    public ChatResponse call(String userInput) {
        memory.add(conversationId, Message.user(userInput));
        ChatResponse resp = chatClient.prompt().messages(memory.get(conversationId)).call();
        String content = resp.content() == null ? "" : resp.content();
        memory.add(conversationId, Message.assistant(content));
        return resp;
    }

    /** 流式提问：载入历史后透传流，并在流结束后回存完整助手回复。 */
    public Stream<ChatResponse> stream(String userInput) {
        memory.add(conversationId, Message.user(userInput));
        Stream<ChatResponse> stream = chatClient.prompt().messages(memory.get(conversationId)).stream();
        StringBuilder collected = new StringBuilder();
        return stream.peek(chunk -> {
            if (chunk.content() != null) {
                collected.append(chunk.content());
            }
        }).onClose(() -> memory.add(conversationId, Message.assistant(collected.toString())));
    }

    /** 清空当前会话记忆。 */
    public void clear() {
        memory.clear(conversationId);
    }

    /** 获取当前会话历史快照。 */
    public List<Message> history() {
        return memory.get(conversationId);
    }
}
