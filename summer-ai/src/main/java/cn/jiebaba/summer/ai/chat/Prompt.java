package cn.jiebaba.summer.ai.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次模型调用的输入：消息列表 + 可选项。 */
public class Prompt {

    private final List<Message> messages;
    private final ChatOptions options;

    public Prompt(List<Message> messages, ChatOptions options) {
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.options = options;
    }

    public Prompt(List<Message> messages) {
        this(messages, null);
    }

    public List<Message> getMessages() {
        return messages;
    }

    public ChatOptions getOptions() {
        return options;
    }
}
