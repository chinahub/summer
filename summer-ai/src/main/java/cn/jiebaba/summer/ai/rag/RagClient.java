package cn.jiebaba.summer.ai.rag;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;

import java.util.List;
import java.util.stream.Stream;

/**
 * RAG 客户端门面：组合 ChatClient 与检索增强顾问，
 * 一步完成「检索资料 + 增强提问 + 调用模型」。
 */
public class RagClient {

    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor advisor;

    public RagClient(ChatClient chatClient, RetrievalAugmentationAdvisor advisor) {
        this.chatClient = chatClient;
        this.advisor = advisor;
    }

    /** 检索增强后同步调用：构造 system+user 提问，增强并交由 ChatClient。 */
    public ChatResponse ask(String systemPrompt, String userQuery) {
        Prompt original = new Prompt(List.of(
                Message.system(systemPrompt == null ? "" : systemPrompt),
                Message.user(userQuery)));
        Prompt augmented = advisor.augment(original);
        return chatClient.prompt().messages(augmented.getMessages()).call();
    }

    /** 检索增强后流式调用。 */
    public Stream<ChatResponse> askStream(String systemPrompt, String userQuery) {
        Prompt original = new Prompt(List.of(
                Message.system(systemPrompt == null ? "" : systemPrompt),
                Message.user(userQuery)));
        Prompt augmented = advisor.augment(original);
        return chatClient.prompt().messages(augmented.getMessages()).stream();
    }
}
