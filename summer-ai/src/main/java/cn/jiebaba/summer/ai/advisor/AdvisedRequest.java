package cn.jiebaba.summer.ai.advisor;

import cn.jiebaba.summer.ai.chat.ChatOptions;
import cn.jiebaba.summer.ai.chat.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 顾问链中的请求载体：承载消息列表、调用选项与跨顾问共享的上下文。
 * 上下文为同一 {@link Map} 实例，沿链传递，供顾问间传递数据。
 * 通过 {@link #withMessages} / {@link #withOptions} 派生新请求时共享同一上下文。
 */
public final class AdvisedRequest {

    private final List<Message> messages;
    private final ChatOptions options;
    private final Map<String, Object> context;

    /**
     * 构造请求，复制消息列表并采用给定上下文（为 null 时新建）。
     *
     * @param messages 消息列表
     * @param options  调用选项
     * @param context  共享上下文，null 时新建空映射
     */
    public AdvisedRequest(List<Message> messages, ChatOptions options, Map<String, Object> context) {
        this.messages = messages == null ? List.of() : new ArrayList<>(messages);
        this.options = options;
        this.context = context == null ? new LinkedHashMap<>() : context;
    }

    /** 以新建空上下文构造请求。 */
    public static AdvisedRequest of(List<Message> messages, ChatOptions options) {
        return new AdvisedRequest(messages, options, new LinkedHashMap<>());
    }

    public List<Message> getMessages() {
        return messages;
    }

    public ChatOptions getOptions() {
        return options;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    /** 派生仅替换消息列表的新请求，共享同一上下文。 */
    public AdvisedRequest withMessages(List<Message> messages) {
        return new AdvisedRequest(messages, this.options, this.context);
    }

    /** 派生仅替换选项的新请求，共享同一上下文。 */
    public AdvisedRequest withOptions(ChatOptions options) {
        return new AdvisedRequest(this.messages, options, this.context);
    }
}
