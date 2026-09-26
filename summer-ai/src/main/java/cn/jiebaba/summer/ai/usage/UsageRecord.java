package cn.jiebaba.summer.ai.usage;

import java.util.Map;

/**
 * 一次维度化用量记录：一次模型调用（或一次累计）的 token 消耗，
 * 以 tags 携带业务维度（如 run/stage/role/instance），供 {@link UsageMeter}
 * 按维度过滤与聚合。{@code calls} 默认 1，重试累计可合成一条（calls 加和）。
 */
public record UsageRecord(String model, Map<String, String> tags,
                          long promptTokens, long completionTokens,
                          long calls, long timestamp) {

    /** 便捷构造：calls=1、时间戳取当前时间。 */
    public static UsageRecord of(String model, Map<String, String> tags,
                                 long promptTokens, long completionTokens) {
        return new UsageRecord(model, tags, promptTokens, completionTokens, 1, System.currentTimeMillis());
    }

    public UsageRecord {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        if (promptTokens < 0 || completionTokens < 0 || calls < 0) {
            throw new IllegalArgumentException("token 数与调用次数不能为负");
        }
    }

    /** 总 token 数（输入 + 输出）。 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
