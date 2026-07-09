package cn.jiebaba.summer.ai.tools;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.AssistantMessage;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatOptions;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.chat.ToolCall;
import cn.jiebaba.summer.ai.chat.ToolDefinition;
import cn.jiebaba.summer.ai.chat.ToolMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 在任意 ChatModel 之上叠加 Function Calling 循环：
 * 注入工具定义 -> 调用模型 -> 若返回工具调用则执行并回填结果 -> 继续调用，
 * 直到模型给出最终回复（无工具调用）或达到最大轮数。
 * 流式调用直接透传底层模型，不支持工具循环。
 */
public class ToolCallingChatModel implements ChatModel {

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final ChatModel delegate;
    private final Map<String, ToolCallback> tools;
    private final int maxIterations;

    public ToolCallingChatModel(ChatModel delegate, List<ToolCallback> tools, int maxIterations) {
        this.delegate = delegate;
        this.tools = new LinkedHashMap<>();
        if (tools != null) {
            for (ToolCallback t : tools) {
                this.tools.put(t.definition().name(), t);
            }
        }
        this.maxIterations = maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : maxIterations;
    }

    public ToolCallingChatModel(ChatModel delegate, List<ToolCallback> tools) {
        this(delegate, tools, DEFAULT_MAX_ITERATIONS);
    }

    /** 同步调用并执行工具循环，返回不含工具调用的最终响应。 */
    @Override
    public ChatResponse call(Prompt prompt) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (ToolCallback t : tools.values()) {
            defs.add(t.definition());
        }
        ChatOptions base = prompt.getOptions();
        List<Message> messages = new ArrayList<>(prompt.getMessages());
        for (int i = 0; i <= maxIterations; i++) {
            ChatResponse resp = delegate.call(new Prompt(messages, withTools(base, defs)));
            if (resp.toolCalls() == null || resp.toolCalls().isEmpty()) {
                return resp;
            }
            messages.add(new AssistantMessage(resp.content(), resp.toolCalls()));
            for (ToolCall tc : resp.toolCalls()) {
                String result = executeTool(tc);
                messages.add(new ToolMessage(tc.id(), result));
            }
        }
        throw new AiException("工具调用超过最大轮数: " + maxIterations);
    }

    /** 流式调用直接透传底层模型，工具循环仅在同步路径执行。 */
    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(prompt);
    }

    /** 执行单个工具调用，未知工具返回错误 JSON。 */
    private String executeTool(ToolCall tc) {
        ToolCallback cb = tools.get(tc.name());
        if (cb == null) {
            return "{\"error\":\"未知工具: " + tc.name() + "\"}";
        }
        return cb.call(tc.arguments());
    }

    /** 将工具定义与原选项合并为新 ChatOptions。 */
    private ChatOptions withTools(ChatOptions base, List<ToolDefinition> defs) {
        ChatOptions.Builder b = ChatOptions.builder();
        if (base != null) {
            if (base.getModel() != null) {
                b.model(base.getModel());
            }
            if (base.getTemperature() != null) {
                b.temperature(base.getTemperature());
            }
            if (base.getMaxTokens() != null) {
                b.maxTokens(base.getMaxTokens());
            }
            if (base.getToolChoice() != null) {
                b.toolChoice(base.getToolChoice());
            }
        }
        b.tools(defs);
        return b.build();
    }
}
