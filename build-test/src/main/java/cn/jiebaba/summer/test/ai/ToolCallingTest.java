package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.ToolCall;
import cn.jiebaba.summer.ai.tools.Tool;
import cn.jiebaba.summer.ai.tools.ToolCallingChatModel;
import cn.jiebaba.summer.ai.tools.ToolParameter;
import cn.jiebaba.summer.core.util.JsonUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Function Calling 工具循环的单元测试（含同步与流式）。 */
public class ToolCallingTest {

    @Test
    public void toolLoopExecutesAndReturnsFinalAnswer() {
        AtomicInteger executed = new AtomicInteger();
        Tool weatherTool = new Tool("getWeather", "查询城市天气",
                List.of(ToolParameter.string("city", "城市名称")),
                args -> {
                    executed.incrementAndGet();
                    return Map.of("city", args.get("city"), "weather", "晴");
                });
        ChatResponse toolCallResp = new ChatResponse(null, null, "tool_calls",
                List.of(new ToolCall("call_1", "getWeather", "{\"city\":\"北京\"}")), null);
        ChatResponse finalResp = new ChatResponse("北京今天晴", null, "stop", null);
        StubChatModel stub = new StubChatModel(toolCallResp, finalResp);

        ChatModel model = new ToolCallingChatModel(stub, List.of(weatherTool));
        ChatResponse resp = ChatClient.create(model).prompt().user("北京天气如何").call();

        Assertions.assertEquals("北京今天晴", resp.content());
        Assertions.assertEquals(1, executed.get(), "工具应被执行一次");
        Assertions.assertEquals(2, stub.callCount(), "底层模型应被调用两次");
    }

    @Test
    public void toolCallWithArgumentsParsed() {
        AtomicInteger seen = new AtomicInteger();
        Tool tool = new Tool("add", "两数相加",
                List.of(ToolParameter.integer("a", "加数"), ToolParameter.integer("b", "被加数")),
                args -> {
                    seen.incrementAndGet();
                    int a = ((Number) args.get("a")).intValue();
                    int b = ((Number) args.get("b")).intValue();
                    return Map.of("sum", a + b);
                });
        String result = tool.call(JsonUtil.toJsonStr(Map.of("a", 3, "b", 4)));
        Assertions.assertEquals(1, seen.get());
        Assertions.assertTrue(result.contains("\"sum\":7"), "结果应包含 sum=7，实际: " + result);
    }

    @Test
    public void unknownToolReturnsErrorJson() {
        ChatResponse toolCallResp = new ChatResponse(null, null, "tool_calls",
                List.of(new ToolCall("call_1", "nope", "{}")), null);
        ChatResponse finalResp = new ChatResponse("完成", null, "stop", null);
        StubChatModel stub = new StubChatModel(toolCallResp, finalResp);
        ChatModel model = new ToolCallingChatModel(stub, List.of());
        ChatResponse resp = ChatClient.create(model).prompt().user("x").call();
        Assertions.assertEquals("完成", resp.content());
        Assertions.assertEquals(2, stub.callCount());
    }

    @Test
    public void maxIterationsStopsRunaway() {
        ChatResponse loop = new ChatResponse(null, null, "tool_calls",
                List.of(new ToolCall("c", "ping", "{}")), null);
        StubChatModel stub = new StubChatModel(loop);
        Tool ping = new Tool("ping", "无操作工具", List.of(),
                args -> "ok");
        ChatModel model = new ToolCallingChatModel(stub, List.of(ping), 2);
        Assertions.assertThrows(cn.jiebaba.summer.ai.AiException.class,
                () -> ChatClient.create(model).prompt().user("loop").call());
    }

    @Test
    public void streamToolLoopExecutesAndStreamsFinalAnswer() {
        AtomicInteger executed = new AtomicInteger();
        AtomicReference<String> capturedCity = new AtomicReference<>();
        Tool weatherTool = new Tool("getWeather", "查询城市天气",
                List.of(ToolParameter.string("city", "城市名称")),
                args -> {
                    executed.incrementAndGet();
                    capturedCity.set((String) args.get("city"));
                    return Map.of("city", args.get("city"), "weather", "晴");
                });
        // 第 1 轮：工具调用增量（参数跨多帧按 index=0 累积）
        List<ChatResponse> round1 = List.of(
                new ChatResponse(null, null, null,
                        List.of(new ToolCall("call_1", "getWeather", "", 0)), null),
                new ChatResponse(null, null, null,
                        List.of(new ToolCall(null, null, "{\"city\":\"", 0)), null),
                new ChatResponse(null, null, null,
                        List.of(new ToolCall(null, null, "北京\"}", 0)), null),
                new ChatResponse(null, null, "tool_calls", List.of(), null));
        // 第 2 轮：最终答案增量
        List<ChatResponse> round2 = List.of(
                new ChatResponse("北京", null, null, List.of(), null),
                new ChatResponse("今天晴", null, null, List.of(), null),
                new ChatResponse(null, null, "stop", List.of(), null));
        StubChatModel stub = new StubChatModel();
        stub.streamRounds(List.of(round1, round2));

        ChatModel model = new ToolCallingChatModel(stub, List.of(weatherTool));
        List<ChatResponse> chunks = ChatClient.create(model).prompt().user("北京天气").stream().toList();

        StringBuilder text = new StringBuilder();
        for (ChatResponse c : chunks) {
            if (c.content() != null) {
                text.append(c.content());
            }
        }
        Assertions.assertEquals("北京今天晴", text.toString());
        Assertions.assertEquals("北京", capturedCity.get(), "工具应收到 city=北京");
        Assertions.assertEquals(1, executed.get(), "工具应执行一次");
        Assertions.assertEquals(2, stub.streamCallCount(), "底层应流式调用两轮");
        Assertions.assertEquals("stop", chunks.get(chunks.size() - 1).finishReason(), "末帧应为 stop 收尾");
    }

    @Test
    public void streamParallelToolCallsAccumulatedByIndex() {
        AtomicInteger aExec = new AtomicInteger();
        AtomicInteger bExec = new AtomicInteger();
        AtomicReference<String> aVal = new AtomicReference<>();
        AtomicReference<String> bVal = new AtomicReference<>();
        Tool setA = new Tool("setA", "设置A",
                List.of(ToolParameter.string("v", "值")),
                args -> {
                    aExec.incrementAndGet();
                    aVal.set((String) args.get("v"));
                    return Map.of("ok", true);
                });
        Tool setB = new Tool("setB", "设置B",
                List.of(ToolParameter.string("v", "值")),
                args -> {
                    bExec.incrementAndGet();
                    bVal.set((String) args.get("v"));
                    return Map.of("ok", true);
                });
        // 第 1 轮：两个并行工具调用，参数片段交错（index 0/1 交替到达）
        List<ChatResponse> round1 = List.of(
                new ChatResponse(null, null, null,
                        List.of(new ToolCall("c1", "setA", "", 0)), null),
                new ChatResponse(null, null, null,
                        List.of(new ToolCall("c2", "setB", "", 1)), null),
                new ChatResponse(null, null, null,
                        List.of(new ToolCall(null, null, "{\"v\":\"A\"}", 0)), null),
                new ChatResponse(null, null, null,
                        List.of(new ToolCall(null, null, "{\"v\":\"B\"}", 1)), null),
                new ChatResponse(null, null, "tool_calls", List.of(), null));
        List<ChatResponse> round2 = List.of(
                new ChatResponse("done", null, "stop", List.of(), null));
        StubChatModel stub = new StubChatModel();
        stub.streamRounds(List.of(round1, round2));

        ChatModel model = new ToolCallingChatModel(stub, List.of(setA, setB));
        List<ChatResponse> chunks = ChatClient.create(model).prompt().user("go").stream().toList();

        Assertions.assertEquals(1, aExec.get(), "setA 应执行一次");
        Assertions.assertEquals(1, bExec.get(), "setB 应执行一次");
        Assertions.assertEquals("A", aVal.get(), "setA 应收到 v=A（index 路由正确）");
        Assertions.assertEquals("B", bVal.get(), "setB 应收到 v=B（index 路由正确）");
        Assertions.assertEquals("done", chunks.get(chunks.size() - 1).content());
    }

    @Test
    public void streamMaxIterationsStopsRunaway() {
        Tool ping = new Tool("ping", "无操作工具", List.of(), args -> "ok");
        List<ChatResponse> loop = List.of(
                new ChatResponse(null, null, null,
                        List.of(new ToolCall("c", "ping", "{}", 0)), null),
                new ChatResponse(null, null, "tool_calls", List.of(), null));
        StubChatModel stub = new StubChatModel();
        stub.streamRounds(List.of(loop));
        ChatModel model = new ToolCallingChatModel(stub, List.of(ping), 2);
        Assertions.assertThrows(cn.jiebaba.summer.ai.AiException.class,
                () -> ChatClient.create(model).prompt().user("loop").stream().toList());
    }

    @Test
    public void streamUnknownToolContinuesToFinalAnswer() {
        List<ChatResponse> round1 = List.of(
                new ChatResponse(null, null, null,
                        List.of(new ToolCall("call_1", "nope", "{}", 0)), null),
                new ChatResponse(null, null, "tool_calls", List.of(), null));
        List<ChatResponse> round2 = List.of(
                new ChatResponse("完成", null, "stop", List.of(), null));
        StubChatModel stub = new StubChatModel();
        stub.streamRounds(List.of(round1, round2));
        ChatModel model = new ToolCallingChatModel(stub, List.of());
        List<ChatResponse> chunks = ChatClient.create(model).prompt().user("x").stream().toList();
        Assertions.assertEquals("完成", chunks.get(chunks.size() - 1).content());
        Assertions.assertEquals(2, stub.streamCallCount(), "底层应流式调用两轮");
    }
}
