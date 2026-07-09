package cn.jiebaba.summer.ai.memory;

import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.SystemMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于消息窗口的内存记忆：每个会话保留最近 maxMessages 条，
 * 始终保留首条 system 消息（若存在）以维持全局人设。
 * 线程安全，适合单机多会话场景。
 */
public class MessageWindowChatMemory implements ChatMemory {

    private final int maxMessages;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Message>> store = new ConcurrentHashMap<>();

    public MessageWindowChatMemory(int maxMessages) {
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages 必须为正");
        }
        this.maxMessages = maxMessages;
    }

    public MessageWindowChatMemory() {
        this(20);
    }

    /** 追加单条消息，并在超过窗口时按规则裁剪。 */
    @Override
    public void add(String conversationId, Message message) {
        if (conversationId == null || message == null) {
            return;
        }
        List<Message> history = store.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        synchronized (history) {
            history.add(message);
            trim(history);
        }
    }

    /** 批量追加消息。 */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        List<Message> history = store.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        synchronized (history) {
            history.addAll(messages);
            trim(history);
        }
    }

    /** 返回会话历史快照（按窗口裁剪后）。 */
    @Override
    public List<Message> get(String conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        CopyOnWriteArrayList<Message> history = store.get(conversationId);
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /** 清空指定会话历史。 */
    @Override
    public void clear(String conversationId) {
        if (conversationId != null) {
            store.remove(conversationId);
        }
    }

    /** 裁剪至窗口大小：保留首条 system 消息与最近若干条非首 system 消息。 */
    private void trim(List<Message> history) {
        while (history.size() > maxMessages) {
            int removeIndex = 0;
            // 保留首条 system 消息：若第一条是 system 则从第二条开始删除
            if (!history.isEmpty() && history.get(0) instanceof SystemMessage) {
                removeIndex = 1;
                if (removeIndex >= history.size()) {
                    break;
                }
            }
            history.remove(removeIndex);
        }
    }
}
