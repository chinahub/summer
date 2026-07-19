package cn.jiebaba.summer.ai.logging;

import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.ChatResponseMetadata;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 在任意 ChatModel 之上叠加调用日志：每次 call/stream 完成后采集模型、token 用量、耗时与成败，
 * 交由 {@link AiCallLogger} 记录。日志写入异常被吞掉且仅首次输出到标准错误，不影响主调用链。
 * 流式调用经 peek 捕获末帧 usage、onClose 落库（调用方应及时关闭返回的 Stream 以触发记录与释放连接）。
 */
public class LoggingChatModel implements ChatModel {

    private static final int QUERY_SUMMARY_LIMIT = 200;

    private final ChatModel delegate;
    private final AiCallLogger logger;
    private volatile boolean loggerErrorReported = false;

    public LoggingChatModel(ChatModel delegate, AiCallLogger logger) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 不能为空");
        }
        if (logger == null) {
            throw new IllegalArgumentException("logger 不能为空");
        }
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        long start = System.currentTimeMillis();
        String querySummary = summarize(prompt);
        try {
            ChatResponse resp = delegate.call(prompt);
            log(resp != null ? resp.metadata() : null, System.currentTimeMillis() - start, true, null, querySummary);
            return resp;
        } catch (RuntimeException e) {
            log(null, System.currentTimeMillis() - start, false, e.getMessage(), querySummary);
            throw e;
        }
    }

    /** 流式调用：peek 捕获末帧 usage，onClose 落库；调用方应关闭 Stream 以触发记录与释放连接。 */
    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        long start = System.currentTimeMillis();
        String querySummary = summarize(prompt);
        AtomicReference<ChatResponseMetadata> lastMeta = new AtomicReference<>();
        try {
            return delegate.stream(prompt)
                    .peek(chunk -> {
                        if (chunk.metadata() != null) {
                            lastMeta.set(chunk.metadata());
                        }
                    })
                    .onClose(() -> log(lastMeta.get(), System.currentTimeMillis() - start, true, null, querySummary));
        } catch (RuntimeException e) {
            log(null, System.currentTimeMillis() - start, false, e.getMessage(), querySummary);
            throw e;
        }
    }

    /** 组装日志记录并写入；logger 自身异常被吞掉，仅首次输出到标准错误便于排查。 */
    private void log(ChatResponseMetadata meta, long latencyMillis, boolean success, String error, String querySummary) {
        try {
            logger.log(new AiCallLog(
                    meta != null ? meta.model() : null,
                    meta != null ? meta.promptTokens() : null,
                    meta != null ? meta.completionTokens() : null,
                    meta != null ? meta.totalTokens() : null,
                    latencyMillis,
                    success,
                    error,
                    querySummary));
        } catch (RuntimeException e) {
            if (!loggerErrorReported) {
                loggerErrorReported = true;
                System.err.println("[summer-ai] AiCallLogger 写入失败（后续错误将静默）: " + e.getMessage());
            }
        }
    }

    /** 提取首条 user 消息作为提问摘要，超长截断。 */
    private static String summarize(Prompt prompt) {
        if (prompt == null || prompt.getMessages() == null) {
            return null;
        }
        for (Message m : prompt.getMessages()) {
            if ("user".equals(m.role()) && m.content() != null && !m.content().isEmpty()) {
                String c = m.content();
                return c.length() > QUERY_SUMMARY_LIMIT ? c.substring(0, QUERY_SUMMARY_LIMIT) + "..." : c;
            }
        }
        return null;
    }
}
