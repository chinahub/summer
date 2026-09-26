package cn.jiebaba.summer.ai.usage;

/**
 * 用量聚合结果：输入/输出 token 与调用次数的求和值。
 * 恒等式：任意维度拆分的合计 === 不拆分的总计（聚合实现须保持可加性）。
 */
public record UsageTotal(long promptTokens, long completionTokens, long calls) {

    /** 零值。 */
    public static final UsageTotal ZERO = new UsageTotal(0, 0, 0);

    /** 总 token 数（输入 + 输出）。 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    /** 两个聚合值相加（按维度拆分后再求和时的合并原语）。 */
    public UsageTotal plus(UsageTotal other) {
        if (other == null) return this;
        return new UsageTotal(promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                calls + other.calls);
    }
}
