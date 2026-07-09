package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.ToolCall;
import cn.jiebaba.summer.ai.tools.Tool;
import cn.jiebaba.summer.ai.tools.ToolCallingChatModel;
import cn.jiebaba.summer.ai.tools.ToolParameter;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.JsonUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Function Calling 工具循环的单元测试。 */
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

        Assert.assertEquals("北京今天晴", resp.content());
        Assert.assertEquals(1, executed.get(), "工具应被执行一次");
        Assert.assertEquals(2, stub.callCount(), "底层模型应被调用两次");
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
        Assert.assertEquals(1, seen.get());
        Assert.assertTrue(result.contains("\"sum\":7"), "结果应包含 sum=7，实际: " + result);
    }

    @Test
    public void unknownToolReturnsErrorJson() {
        ChatResponse toolCallResp = new ChatResponse(null, null, "tool_calls",
                List.of(new ToolCall("call_1", "nope", "{}")), null);
        ChatResponse finalResp = new ChatResponse("完成", null, "stop", null);
        StubChatModel stub = new StubChatModel(toolCallResp, finalResp);
        ChatModel model = new ToolCallingChatModel(stub, List.of());
        ChatResponse resp = ChatClient.create(model).prompt().user("x").call();
        Assert.assertEquals("完成", resp.content());
        Assert.assertEquals(2, stub.callCount());
    }

    @Test
    public void maxIterationsStopsRunaway() {
        ChatResponse loop = new ChatResponse(null, null, "tool_calls",
                List.of(new ToolCall("c", "ping", "{}")), null);
        StubChatModel stub = new StubChatModel(loop);
        Tool ping = new Tool("ping", "无操作工具", List.of(),
                args -> "ok");
        ChatModel model = new ToolCallingChatModel(stub, List.of(ping), 2);
        Assert.assertThrows(cn.jiebaba.summer.ai.AiException.class,
                () -> ChatClient.create(model).prompt().user("loop").call());
    }
}
