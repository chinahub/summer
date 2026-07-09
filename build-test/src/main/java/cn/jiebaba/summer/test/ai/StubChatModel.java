package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 测试桩 ChatModel：按脚本返回响应，并可指定前 N 次调用抛出异常，用于工具循环/记忆/弹性/RAG 测试。
 */
public class StubChatModel implements ChatModel {

    private final List<ChatResponse> scripted;
    private final AtomicInteger callIndex = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int failFirstN = 0;
    private volatile Prompt lastPrompt;

    public StubChatModel(ChatResponse... responses) {
        this.scripted = new ArrayList<>(List.of(responses));
    }

    public StubChatModel failFirstN(int n) {
        this.failFirstN = n;
        return this;
    }

    public int callCount() {
        return calls.get();
    }

    public Prompt lastPrompt() {
        return lastPrompt;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        int n = calls.incrementAndGet();
        if (n <= failFirstN) {
            throw new AiException("模拟失败 #" + n);
        }
        if (scripted.isEmpty()) {
            return new ChatResponse("ok", null, "stop", null);
        }
        int idx = Math.min(callIndex.getAndIncrement(), scripted.size() - 1);
        return scripted.get(idx);
    }

    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        return Stream.of(new ChatResponse("hi", null, "stop", null));
    }
}
