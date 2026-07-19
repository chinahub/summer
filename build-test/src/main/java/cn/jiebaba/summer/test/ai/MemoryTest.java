package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.AssistantMessage;
import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.memory.MemoryChatClient;
import cn.jiebaba.summer.ai.memory.MessageWindowChatMemory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 对话记忆与会话管理的单元测试。 */
public class MemoryTest {

    @Test
    public void windowKeepsSystemAndTrims() {
        MessageWindowChatMemory memory = new MessageWindowChatMemory(3);
        memory.add("s1", Message.system("你是助手"));
        memory.add("s1", Message.user("第一问"));
        memory.add("s1", Message.assistant("第一答"));
        memory.add("s1", Message.user("第二问"));
        List<Message> history = memory.get("s1");
        Assertions.assertEquals(3, history.size());
        Assertions.assertEquals("system", history.get(0).role());
        Assertions.assertFalse(containsText(history, "第一问"), "窗口应裁掉最早的非 system 消息");
        Assertions.assertTrue(containsText(history, "第二问"));
    }

    @Test
    public void isolatedConversations() {
        MessageWindowChatMemory memory = new MessageWindowChatMemory(10);
        memory.add("a", Message.user("A 会话"));
        memory.add("b", Message.user("B 会话"));
        Assertions.assertEquals(1, memory.get("a").size());
        Assertions.assertEquals(1, memory.get("b").size());
        Assertions.assertTrue(memory.get("a").get(0).content().contains("A"));
    }

    @Test
    public void clearRemovesHistory() {
        MessageWindowChatMemory memory = new MessageWindowChatMemory(10);
        memory.add("s1", Message.user("hi"));
        memory.clear("s1");
        Assertions.assertTrue(memory.get("s1").isEmpty());
    }

    @Test
    public void memoryClientRoundTrip() {
        StubChatModel stub = new StubChatModel(new ChatResponse("你好呀", null, "stop", null));
        MemoryChatClient client = new MemoryChatClient(
                ChatClient.create(stub), new MessageWindowChatMemory(20), "c1");
        ChatResponse resp = client.call("你好");
        Assertions.assertEquals("你好呀", resp.content());
        List<Message> history = client.history();
        Assertions.assertEquals(2, history.size());
        Assertions.assertEquals("user", history.get(0).role());
        Assertions.assertTrue(history.get(1) instanceof AssistantMessage);
        Assertions.assertEquals("你好呀", history.get(1).content());
    }

    private boolean containsText(List<Message> messages, String text) {
        for (Message m : messages) {
            if (m.content() != null && m.content().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
