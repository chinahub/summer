package cn.jiebaba.summer.ai.usage;

import java.util.Map;

/**
 * 维度化 AI 用量计量：按 tags（run/stage/role/instance 等任意业务维度）记录与聚合
 * token 消耗，替代每个应用自建 {@code SUM ... GROUP BY} 统计。
 * <p>典型用法：数字员工流水线按"实例"计量——每次模型调用后
 * {@code record(UsageRecord.of(model, Map.of("instance", "林", "stage", "DEVELOP"), ...))}，
 * 面板取 {@code sumGroupedBy("instance", Map.of("run", runId))} 渲染；
 * 各实例合计与 {@code sum(Map.of("run", runId))} 的总计一致。
 * <p>实现须保证聚合可加性与线程安全。
 */
public interface UsageMeter {

    /** 记录一次用量。 */
    void record(UsageRecord record);

    /**
     * 按标签过滤求和（所有条目必须全部匹配，AND 语义）；filter 为空/空 Map 表示不过滤。
     */
    UsageTotal sum(Map<String, String> tagFilter);

    /**
     * 按指定维度 key 分组求和（组名为该维度的取值，未含该维度的记录归入 "" 组）；
     * filter 语义同 {@link #sum}。
     */
    Map<String, UsageTotal> sumGroupedBy(String tagKey, Map<String, String> tagFilter);
}
