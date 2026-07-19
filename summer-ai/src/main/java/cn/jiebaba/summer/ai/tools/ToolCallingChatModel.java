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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 在任意 ChatModel 之上叠加 Function Calling 循环：
 * 注入工具定义 -> 调用模型 -> 若返回工具调用则执行并回填结果 -> 继续调用，
 * 直到模型给出最终回复（无工具调用）或达到最大轮数。
 *
 * <p>同步与流式均支持工具循环：
 * <ul>
 *   <li>{@link #call(Prompt)} 同步执行完整循环，返回不含工具调用的最终响应；</li>
 *   <li>{@link #stream(Prompt)} 流式执行循环：逐 token 输出每轮的 content/reasoningContent 增量，
 *       内部按 {@link ToolCall#index()} 跨 SSE 帧累积工具调用片段，轮次结束时若存在工具调用则静默执行并续接下一轮流式，
 *       直到某一轮无工具调用即收尾。tool_call 增量片段帧与内部 "tool_calls" 结束信号不外抛，
 *       故调用方看到的是一条连续的文本流；工具执行在流式线程上同步进行。</li>
 * </ul>
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

    /**
     * 流式调用并执行工具循环：返回一条连续的 content/reasoningContent 增量流，
     * 内部按 index 累积工具调用、轮次结束时静默执行并续接下一轮，直到无工具调用收尾。
     * 调用方应及时消费或关闭返回的 Stream 以释放底层连接。
     */
    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (ToolCallback t : tools.values()) {
            defs.add(t.definition());
        }
        ToolStreamIterator it = new ToolStreamIterator(
                new ArrayList<>(prompt.getMessages()), prompt.getOptions(), defs);
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(it, 0), false)
                .onClose(it::close);
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
            if (base.getResponseFormat() != null) {
                b.responseFormat(base.getResponseFormat());
            }
        }
        b.tools(defs);
        return b.build();
    }

    /**
     * 流式工具循环的状态机迭代器：驱动多轮流式，逐 chunk 累积工具调用片段并选择性地外抛 content/reasoning 增量。
     * 每轮复用底层模型的 Stream（连接/读取由其 onClose 管理），轮次切换时关闭上一轮、按需执行工具后开启下一轮。
     */
    private final class ToolStreamIterator implements Iterator<ChatResponse>, AutoCloseable {

        private final List<ToolDefinition> defs;
        private final ChatOptions base;
        private final List<Message> messages;
        private int iteration = 0;

        private Stream<ChatResponse> curStream;
        private Iterator<ChatResponse> curIter;

        /** 当前轮累积区：content/reasoning 文本与按 index 归集的工具调用片段。 */
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final LinkedHashMap<Integer, ToolCallAccum> toolCalls = new LinkedHashMap<>();

        private ChatResponse prefetched;

        ToolStreamIterator(List<Message> messages, ChatOptions base, List<ToolDefinition> defs) {
            this.messages = messages;
            this.base = base;
            this.defs = defs;
            startRound();
        }

        @Override
        public boolean hasNext() {
            if (prefetched != null) {
                return true;
            }
            try {
                prefetched = advance();
            } catch (RuntimeException e) {
                closeCurrentRound();
                throw e;
            }
            return prefetched != null;
        }

        @Override
        public ChatResponse next() {
            if (prefetched == null && !hasNext()) {
                throw new NoSuchElementException();
            }
            ChatResponse r = prefetched;
            prefetched = null;
            return r;
        }

        @Override
        public void close() {
            closeCurrentRound();
        }

        /**
         * 从当前轮拉取下一个可外抛的增量；当前轮耗尽时按是否存在工具调用决定收尾或续接下一轮。
         * 返回 null 表示整条流结束。纯 tool_call 片段帧与内部 "tool_calls" 结束信号被跳过不外抛。
         */
        private ChatResponse advance() {
            while (true) {
                if (curIter != null && curIter.hasNext()) {
                    ChatResponse chunk = curIter.next();
                    accumulate(chunk);
                    ChatResponse emitted = emit(chunk);
                    if (emitted != null) {
                        return emitted;
                    }
                    continue;
                }
                closeCurrentRound();
                if (toolCalls.isEmpty()) {
                    return null;
                }
                if (iteration >= maxIterations) {
                    throw new AiException("工具调用超过最大轮数: " + maxIterations);
                }
                List<ToolCall> assembled = assembleToolCalls();
                messages.add(new AssistantMessage(content.toString(), assembled));
                for (ToolCall tc : assembled) {
                    messages.add(new ToolMessage(tc.id(), executeTool(tc)));
                }
                iteration++;
                startRound();
            }
        }

        /** 开启下一轮：注入工具定义后调用底层模型 stream，重置累积区。 */
        private void startRound() {
            curStream = delegate.stream(new Prompt(messages, withTools(base, defs)));
            curIter = curStream.iterator();
            content.setLength(0);
            reasoning.setLength(0);
            toolCalls.clear();
        }

        /** 关闭当前轮底层流以释放连接，置空迭代器避免误用。 */
        private void closeCurrentRound() {
            if (curStream != null) {
                try {
                    curStream.close();
                } catch (Exception ignored) {
                    // 忽略关闭异常
                }
                curStream = null;
                curIter = null;
            }
        }

        /**
         * 累积当前 chunk：content/reasoning 追加文本，工具调用按 index 归集
         * （首次片段记录 id/name，后续片段追加 arguments；无 index 时按出现顺序兜底归集）。
         */
        private void accumulate(ChatResponse chunk) {
            if (chunk.content() != null) {
                content.append(chunk.content());
            }
            if (chunk.reasoningContent() != null) {
                reasoning.append(chunk.reasoningContent());
            }
            List<ToolCall> calls = chunk.toolCalls();
            if (calls == null || calls.isEmpty()) {
                return;
            }
            for (ToolCall tc : calls) {
                int idx = tc.index();
                if (idx < 0) {
                    if (tc.id() != null || tc.name() != null) {
                        idx = toolCalls.size();
                    } else if (!toolCalls.isEmpty()) {
                        idx = toolCalls.size() - 1;
                    } else {
                        idx = 0;
                    }
                }
                ToolCallAccum acc = toolCalls.computeIfAbsent(idx, k -> new ToolCallAccum());
                if (tc.id() != null) {
                    acc.id = tc.id();
                }
                if (tc.name() != null) {
                    acc.name = tc.name();
                }
                if (tc.arguments() != null) {
                    acc.arguments.append(tc.arguments());
                }
            }
        }

        /**
         * 决定 chunk 是否外抛：含 content/reasoning 或非 "tool_calls" 结束信号则外抛（剥离 toolCalls），
         * 纯 tool_call 片段帧与内部 "tool_calls" 结束信号返回 null 跳过。
         */
        private ChatResponse emit(ChatResponse chunk) {
            String finish = chunk.finishReason();
            if ("tool_calls".equals(finish)) {
                return null;
            }
            boolean hasContent = chunk.content() != null && !chunk.content().isEmpty();
            boolean hasReasoning = chunk.reasoningContent() != null && !chunk.reasoningContent().isEmpty();
            boolean hasUsage = chunk.metadata() != null;
            if (!hasContent && !hasReasoning && finish == null && !hasUsage) {
                return null;
            }
            return new ChatResponse(chunk.content(), chunk.reasoningContent(), finish, List.of(), chunk.metadata());
        }

        /** 按 index 升序组装当前轮累积的工具调用为完整 ToolCall 列表（index 置 -1，用于历史回放）。 */
        private List<ToolCall> assembleToolCalls() {
            List<ToolCall> list = new ArrayList<>(toolCalls.size());
            toolCalls.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> list.add(e.getValue().toToolCall()));
            return list;
        }
    }

    /** 工具调用片段累积器：跨 SSE 帧拼装 id/name/arguments。 */
    private static final class ToolCallAccum {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        /** 组装为完整 ToolCall（index=-1，适用于历史回放与非流式语义）。 */
        ToolCall toToolCall() {
            return new ToolCall(id, name, arguments.toString());
        }
    }
}
