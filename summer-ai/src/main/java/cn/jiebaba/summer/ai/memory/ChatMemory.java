package cn.jiebaba.summer.ai.memory;

import cn.jiebaba.summer.ai.chat.Message;

import java.util.List;

/**
 * 对话记忆抽象：按会话 id 维护消息历史，供多轮上下文与会话管理复用。
 * 实现可为内存窗口、持久化存储等。
 */
public interface ChatMemory {

    /** 追加单条消息到指定会话。 */
    void add(String conversationId, Message message);

    /** 批量追加消息到指定会话。 */
    void add(String conversationId, List<Message> messages);

    /** 获取指定会话的当前历史消息（不可变视图）。 */
    List<Message> get(String conversationId);

    /** 清空指定会话的历史。 */
    void clear(String conversationId);
}
