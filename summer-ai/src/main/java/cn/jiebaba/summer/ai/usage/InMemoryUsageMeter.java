package cn.jiebaba.summer.ai.usage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存版 {@link UsageMeter}：记录保存在进程内，查询时现算聚合。
 * 适合无数据库场景与测试；重启即失。记录量大时应改用 JdbcUsageMeter。
 */
public final class InMemoryUsageMeter implements UsageMeter {

    private final List<UsageRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void record(UsageRecord record) {
        if (record == null) throw new IllegalArgumentException("record 不能为空");
        records.add(record);
    }

    @Override
    public UsageTotal sum(Map<String, String> tagFilter) {
        UsageTotal total = UsageTotal.ZERO;
        for (UsageRecord r : records) {
            if (matches(r, tagFilter)) total = total.plus(toTotal(r));
        }
        return total;
    }

    @Override
    public Map<String, UsageTotal> sumGroupedBy(String tagKey, Map<String, String> tagFilter) {
        Map<String, UsageTotal> grouped = new LinkedHashMap<>();
        for (UsageRecord r : records) {
            if (!matches(r, tagFilter)) continue;
            String key = r.tags().getOrDefault(tagKey, "");
            grouped.merge(key, toTotal(r), UsageTotal::plus);
        }
        return grouped;
    }

    /** 当前记录条数（测试/诊断用）。 */
    public int size() {
        return records.size();
    }

    static boolean matches(UsageRecord r, Map<String, String> tagFilter) {
        if (tagFilter == null || tagFilter.isEmpty()) return true;
        for (Map.Entry<String, String> e : tagFilter.entrySet()) {
            if (!e.getValue().equals(r.tags().get(e.getKey()))) return false;
        }
        return true;
    }

    static UsageTotal toTotal(UsageRecord r) {
        return new UsageTotal(r.promptTokens(), r.completionTokens(), r.calls());
    }
}
