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
 * 流式支持按「轮」脚本：每轮为一组增量 ChatResponse（含工具调用片段），stream() 依次取出下一轮流式返回。
 */
public class StubChatModel implements ChatModel {

    private final List<ChatResponse> scripted;
    private final AtomicInteger callIndex = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int failFirstN = 0;
    private volatile Prompt lastPrompt;

    private final List<List<ChatResponse>> streamScript = new ArrayList<>();
    private final AtomicInteger streamIndex = new AtomicInteger();

    public StubChatModel(ChatResponse... responses) {
        this.scripted = new ArrayList<>(List.of(responses));
    }

    public StubChatModel failFirstN(int n) {
        this.failFirstN = n;
        return this;
    }

    /** 配置流式脚本：每轮为一组增量响应片段，stream() 依次返回下一轮；未配置时流式回退单帧 "hi"。 */
    public StubChatModel streamRounds(List<List<ChatResponse>> rounds) {
        this.streamScript.clear();
        if (rounds != null) {
            for (List<ChatResponse> r : rounds) {
                this.streamScript.add(new ArrayList<>(r));
            }
        }
        return this;
    }

    public int callCount() {
        return calls.get();
    }

    public int streamCallCount() {
        return streamIndex.get();
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

    /** 流式调用：按 streamRounds 脚本依次返回下一轮增量；脚本耗尽后重复最后一轮（便于测试超限循环）。 */
    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        lastPrompt = prompt;
        int n = streamIndex.incrementAndGet();
        if (streamScript.isEmpty()) {
            return Stream.of(new ChatResponse("hi", null, "stop", null));
        }
        int idx = Math.min(n - 1, streamScript.size() - 1);
        return new ArrayList<>(streamScript.get(idx)).stream();
    }
}
