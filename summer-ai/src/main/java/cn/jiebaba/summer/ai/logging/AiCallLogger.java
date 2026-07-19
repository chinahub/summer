package cn.jiebaba.summer.ai.logging;

/**
 * 大模型调用日志 sink：由 {@link LoggingChatModel} 在每次 LLM 调用后回调记录。
 * 实现可写数据库（如 summer-boot 的 {@code JdbcAiCallLogger}）、日志框架或外部观测系统。
 * 实现应吞掉自身异常，避免影响主调用链（{@link LoggingChatModel} 亦会兜底捕获）。
 */
public interface AiCallLogger {

    /** 记录一次调用；record 不为 null。 */
    void log(AiCallLog record);
}
