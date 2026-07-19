package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.ChatResponseMetadata;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.logging.AiCallLog;
import cn.jiebaba.summer.ai.logging.AiCallLogger;
import cn.jiebaba.summer.ai.logging.LoggingChatModel;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * LoggingChatModel 装饰器单元测试：验证同步/流式调用均能采集模型名、token 用量、耗时与成败，
 * 且日志写入异常不影响主调用链。使用 {@link CollectingLogger} 收集记录，无需数据库与网络。
 */
public class AiCallLogTest {

    private Prompt prompt() {
        return new Prompt(List.of(Message.user("hi")));
    }

    /** 同步调用成功：记录模型名、token 用量、耗时与提问摘要，success=true。 */
    @Test
    public void callLogsTokenUsageAndLatency() {
        ChatResponseMetadata meta = new ChatResponseMetadata("deepseek-chat", 10L, 20L, 30L, null);
        StubChatModel stub = new StubChatModel(new ChatResponse("ok", null, "stop", meta));
        CollectingLogger logger = new CollectingLogger();
        LoggingChatModel model = new LoggingChatModel(stub, logger);

        ChatResponse resp = model.call(prompt());

        Assert.assertEquals("ok", resp.content());
        Assert.assertEquals(1, logger.records.size(), "应记录一次调用");
        AiCallLog rec = logger.records.get(0);
        Assert.assertEquals("deepseek-chat", rec.model());
        Assert.assertEquals(Long.valueOf(10L), rec.promptTokens());
        Assert.assertEquals(Long.valueOf(20L), rec.completionTokens());
        Assert.assertEquals(Long.valueOf(30L), rec.totalTokens());
        Assert.assertTrue(rec.success(), "成功调用应记录 success=true");
        Assert.assertTrue(rec.latencyMillis() >= 0, "耗时非负");
        Assert.assertEquals("hi", rec.querySummary());
    }

    /** 同步调用失败：异常向上抛出，且记录 success=false 与错误信息。 */
    @Test
    public void callLogsFailureOnException() {
        StubChatModel stub = new StubChatModel().failFirstN(99);
        CollectingLogger logger = new CollectingLogger();
        LoggingChatModel model = new LoggingChatModel(stub, logger);

        Assert.assertThrows(AiException.class, () -> model.call(prompt()));

        Assert.assertEquals(1, logger.records.size(), "失败也应记录一次");
        AiCallLog rec = logger.records.get(0);
        Assert.assertFalse(rec.success(), "失败调用应记录 success=false");
        Assert.assertNotNull(rec.errorMessage(), "应记录错误信息");
        Assert.assertNull(rec.model(), "无元数据时 model 为 null");
    }

    /** 流式调用：peek 捕获末帧 usage，关闭 Stream 后记录模型与 token 用量。 */
    @Test
    public void streamLogsUsageOnClose() {
        ChatResponse contentFrame = new ChatResponse("hello", null, null, null);
        ChatResponseMetadata meta = new ChatResponseMetadata("deepseek-chat", 5L, 15L, 20L, null);
        ChatResponse usageFrame = new ChatResponse(null, null, "stop", meta);
        StubChatModel stub = new StubChatModel().streamRounds(List.of(List.of(contentFrame, usageFrame)));
        CollectingLogger logger = new CollectingLogger();
        LoggingChatModel model = new LoggingChatModel(stub, logger);

        try (Stream<ChatResponse> s = model.stream(prompt())) {
            List<ChatResponse> frames = s.toList();
            Assert.assertEquals(2, frames.size(), "应收到 2 个流式帧");
        }

        Assert.assertEquals(1, logger.records.size(), "关闭 Stream 后应记录一次");
        AiCallLog rec = logger.records.get(0);
        Assert.assertEquals("deepseek-chat", rec.model());
        Assert.assertEquals(Long.valueOf(20L), rec.totalTokens());
        Assert.assertTrue(rec.success());
        Assert.assertEquals("hi", rec.querySummary());
    }

    /** 日志写入异常被吞掉，不影响主调用链返回结果。 */
    @Test
    public void loggerExceptionSwallowedAndCallSucceeds() {
        StubChatModel stub = new StubChatModel(new ChatResponse("ok", null, "stop", null));
        AiCallLogger throwingLogger = rec -> { throw new RuntimeException("sink 故障"); };
        LoggingChatModel model = new LoggingChatModel(stub, throwingLogger);

        ChatResponse resp = model.call(prompt());

        Assert.assertEquals("ok", resp.content(), "logger 异常不应影响主调用结果");
    }

    /** 收集型 AiCallLogger：将每次记录存入列表，便于测试断言。 */
    static final class CollectingLogger implements AiCallLogger {
        final List<AiCallLog> records = new ArrayList<>();

        @Override
        public void log(AiCallLog record) {
            records.add(record);
        }
    }
}
