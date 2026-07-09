package cn.jiebaba.summer.ai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * ChatModel 的 fluent 门面：链式拼装消息与选项后发起同步或流式调用。
 * 用法：ChatClient.create(model).prompt().system("你是助手").user("你好").call()
 */
public class ChatClient {

    private final ChatModel chatModel;

    private ChatClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public static ChatClient create(ChatModel chatModel) {
        return new ChatClient(chatModel);
    }

    public PromptBuilder prompt() {
        return new PromptBuilder(chatModel);
    }

    public PromptBuilder prompt(String systemText) {
        return new PromptBuilder(chatModel).system(systemText);
    }

    /** 链式请求构造器，累积消息与选项。 */
    public static class PromptBuilder {
        private final ChatModel chatModel;
        private final List<Message> messages = new ArrayList<>();
        private ChatOptions options;

        PromptBuilder(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        public PromptBuilder system(String content) {
            messages.add(new SystemMessage(content));
            return this;
        }

        public PromptBuilder user(String content) {
            messages.add(new UserMessage(content));
            return this;
        }

        public PromptBuilder assistant(String content) {
            messages.add(new AssistantMessage(content));
            return this;
        }

        /** 直接追加消息列表，便于回放历史或注入多模态/工具消息。 */
        public PromptBuilder messages(List<Message> messages) {
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return this;
        }

        public PromptBuilder options(ChatOptions options) {
            this.options = options;
            return this;
        }

        public ChatResponse call() {
            return chatModel.call(new Prompt(messages, options));
        }

        public Stream<ChatResponse> stream() {
            return chatModel.stream(new Prompt(messages, options));
        }
    }
}
