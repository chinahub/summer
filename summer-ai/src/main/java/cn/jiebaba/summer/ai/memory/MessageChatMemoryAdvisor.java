package cn.jiebaba.summer.ai.memory;

import cn.jiebaba.summer.ai.advisor.AdvisedRequest;
import cn.jiebaba.summer.ai.advisor.AdvisedResponse;
import cn.jiebaba.summer.ai.advisor.Advisor;
import cn.jiebaba.summer.ai.advisor.StreamAdvisorChain;
import cn.jiebaba.summer.ai.chat.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 记忆顾问：按会话 id 在调用前载入历史、调用后回存用户与助手消息，
 * 使多轮上下文由顾问链自动维护，调用方无需手动拼接历史。
 *
 * <p>载入策略：将请求中的 user 消息持久化，再以「请求中的非 user 消息（如 system）
 * + 历史消息」作为发送给模型的消息；流式模式在流关闭后回存完整助手回复。
 */
public class MessageChatMemoryAdvisor implements Advisor {

    /** 记忆顾问默认顺序：最先处理请求（载入历史），最后处理响应（回存）。 */
    public static final int ORDER = 100;

    private final ChatMemory memory;
    private final String conversationId;

    public MessageChatMemoryAdvisor(ChatMemory memory, String conversationId) {
        this.memory = memory;
        this.conversationId = conversationId;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 请求前置：持久化当前 user 消息，载入历史，重组为「非 user 消息 + 历史」发送。
     */
    @Override
    public AdvisedRequest beforeCall(AdvisedRequest request) {
        List<Message> nonUser = new ArrayList<>();
        for (Message message : request.getMessages()) {
            if ("user".equals(message.role())) {
                memory.add(conversationId, message);
            } else {
                nonUser.add(message);
            }
        }
        List<Message> sent = new ArrayList<>(nonUser);
        sent.addAll(memory.get(conversationId));
        return request.withMessages(sent);
    }

    /**
     * 响应后置：回存助手回复，内容为 null 时按空串存储。
     */
    @Override
    public AdvisedResponse afterCall(AdvisedResponse response) {
        String content = response.response().content();
        memory.add(conversationId, Message.assistant(content == null ? "" : content));
        return response;
    }

    /**
     * 流式调用：载入历史后透传流，逐片段收集内容，流关闭时回存完整助手回复。
     */
    @Override
    public Stream<AdvisedResponse> adviseStream(AdvisedRequest request, StreamAdvisorChain chain) {
        AdvisedRequest prepared = beforeCall(request);
        StringBuilder collected = new StringBuilder();
        return chain.advanceStream(prepared)
                .peek(chunk -> {
                    String content = chunk.response().content();
                    if (content != null) {
                        collected.append(content);
                    }
                })
                .onClose(() -> memory.add(conversationId, Message.assistant(collected.toString())));
    }
}
