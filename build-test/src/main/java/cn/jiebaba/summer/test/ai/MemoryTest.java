package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.AssistantMessage;
import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.memory.MemoryChatClient;
import cn.jiebaba.summer.ai.memory.MessageWindowChatMemory;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

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
        Assert.assertEquals(3, history.size());
        Assert.assertEquals("system", history.get(0).role());
        Assert.assertFalse(containsText(history, "第一问"), "窗口应裁掉最早的非 system 消息");
        Assert.assertTrue(containsText(history, "第二问"));
    }

    @Test
    public void isolatedConversations() {
        MessageWindowChatMemory memory = new MessageWindowChatMemory(10);
        memory.add("a", Message.user("A 会话"));
        memory.add("b", Message.user("B 会话"));
        Assert.assertEquals(1, memory.get("a").size());
        Assert.assertEquals(1, memory.get("b").size());
        Assert.assertTrue(memory.get("a").get(0).content().contains("A"));
    }

    @Test
    public void clearRemovesHistory() {
        MessageWindowChatMemory memory = new MessageWindowChatMemory(10);
        memory.add("s1", Message.user("hi"));
        memory.clear("s1");
        Assert.assertTrue(memory.get("s1").isEmpty());
    }

    @Test
    public void memoryClientRoundTrip() {
        StubChatModel stub = new StubChatModel(new ChatResponse("你好呀", null, "stop", null));
        MemoryChatClient client = new MemoryChatClient(
                ChatClient.create(stub), new MessageWindowChatMemory(20), "c1");
        ChatResponse resp = client.call("你好");
        Assert.assertEquals("你好呀", resp.content());
        List<Message> history = client.history();
        Assert.assertEquals(2, history.size());
        Assert.assertEquals("user", history.get(0).role());
        Assert.assertTrue(history.get(1) instanceof AssistantMessage);
        Assert.assertEquals("你好呀", history.get(1).content());
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
