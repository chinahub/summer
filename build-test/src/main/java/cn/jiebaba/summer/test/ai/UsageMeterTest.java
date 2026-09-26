package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.usage.InMemoryUsageMeter;
import cn.jiebaba.summer.ai.usage.UsageMeter;
import cn.jiebaba.summer.ai.usage.UsageRecord;
import cn.jiebaba.summer.ai.usage.UsageTotal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * {@link UsageMeter} 维度化计量单测：按维度过滤/分组聚合、可加性恒等式
 * （各实例合计 === 不拆分总计——v1.1 验收口径）。
 */
public class UsageMeterTest {

    @Test
    @DisplayName("按标签过滤求和")
    void sumWithFilter() {
        UsageMeter meter = new InMemoryUsageMeter();
        meter.record(UsageRecord.of("kimi", Map.of("run", "1", "instance", "林"), 100, 50));
        meter.record(UsageRecord.of("kimi", Map.of("run", "1", "instance", "苏"), 200, 30));
        meter.record(UsageRecord.of("kimi", Map.of("run", "2", "instance", "林"), 999, 999));

        UsageTotal run1 = meter.sum(Map.of("run", "1"));
        Assertions.assertEquals(300, run1.promptTokens());
        Assertions.assertEquals(80, run1.completionTokens());
        Assertions.assertEquals(2, run1.calls());

        UsageTotal all = meter.sum(Map.of());
        Assertions.assertEquals(1299, all.promptTokens());
        Assertions.assertEquals(3, all.calls());
    }

    @Test
    @DisplayName("按实例分组聚合，且实例合计 === 总计（可加性恒等式）")
    void groupedByInstanceAddsUp() {
        UsageMeter meter = new InMemoryUsageMeter();
        meter.record(UsageRecord.of("kimi", Map.of("run", "1", "instance", "林", "stage", "DEVELOP"), 100, 50));
        meter.record(UsageRecord.of("kimi", Map.of("run", "1", "instance", "苏", "stage", "DEVELOP"), 200, 30));
        meter.record(UsageRecord.of("kimi", Map.of("run", "1", "instance", "林", "stage", "TEST"), 10, 10));

        Map<String, UsageTotal> byInstance = meter.sumGroupedBy("instance", Map.of("run", "1"));
        Assertions.assertEquals(2, byInstance.size());
        Assertions.assertEquals(170, byInstance.get("林").totalTokens());
        Assertions.assertEquals(230, byInstance.get("苏").totalTokens());

        long instanceSum = byInstance.values().stream().mapToLong(UsageTotal::totalTokens).sum();
        Assertions.assertEquals(meter.sum(Map.of("run", "1")).totalTokens(), instanceSum,
                "实例合计必须等于阶段/总计口径");

        // 未含该维度的记录归入 "" 组
        meter.record(UsageRecord.of("kimi", Map.of("run", "1"), 1, 1));
        Assertions.assertTrue(meter.sumGroupedBy("instance", Map.of("run", "1")).containsKey(""));
    }

    @Test
    @DisplayName("UsageTotal.plus 与 UsageRecord 恒等式")
    void totalsAdditive() {
        UsageTotal a = new UsageTotal(100, 50, 1);
        UsageTotal b = new UsageTotal(200, 30, 2);
        UsageTotal c = a.plus(b);
        Assertions.assertEquals(300, c.promptTokens());
        Assertions.assertEquals(80, c.completionTokens());
        Assertions.assertEquals(3, c.calls());
        Assertions.assertEquals(380, c.totalTokens());

        UsageRecord r = UsageRecord.of("m", Map.of("k", "v"), 7, 8);
        Assertions.assertEquals(15, r.totalTokens());
        Assertions.assertEquals(1, r.calls());
    }
}
