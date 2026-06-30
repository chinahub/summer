# 定时任务（summer-core）

基于**虚拟线程自循环**实现，零第三方依赖。每个 `@Scheduled` 方法独占一个虚拟线程，按自身节奏循环触发；阻塞 IO 会自动让出平台载波线程，任务之间互不影响调度精度。支持 cron 表达式与固定速率/固定延迟两种模式。

## 注解

`@Scheduled`（可重复，标注在方法上）：

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `cron` | `""` | 5 段 cron：`分 时 日 月 周`（如 `*/5 * * * *`→每 5 分钟） |
| `fixedRate` | `-1` | 固定速率（毫秒）：从上次**开始**到下次**开始**的间隔 |
| `fixedDelay` | `-1` | 固定延迟（毫秒）：从上次**结束**到下次**开始**的间隔 |
| `initialDelay` | `0` | 首次执行前的延迟（毫秒）；对 cron 表示首次触发不得早于该延迟 |

`cron` 与 `fixedRate/fixedDelay` 二选一；同时配置时以 `cron` 优先。被标注方法必须**无参**。

## Cron 表达式

5 个字段，空格分隔：

```
分(0-59)  时(0-23)  日(1-31)  月(1-12)  周(0-6, 0=周日)
```

支持：`*`（任意）、`,`（列表）、`-`（范围）、`/`（步长，如 `*/5`），月份与周可用名称（`jan..dec`、`sun..sat`）。例如 `0 9 * * 1-5` 表示工作日每天 9:00。

**日与周的组合规则**（遵循 Vixie cron）：当「日」与「周」都被限定（均不含 `*`）时，满足**任一**即触发；否则两者需同时满足。

## 示例

```java
@Component
public class HeartbeatTask {
    private static final Logger LOG = Logger.getLogger(HeartbeatTask.class.getName());
    private final AtomicInteger beats = new AtomicInteger(0);

    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    public void beat() {
        LOG.info("heartbeat #" + beats.incrementAndGet());
    }
}
```

每 60 秒一次，首次延迟 5 秒。

## 运行模型

- `ScheduledTaskRegistrar` 为每个 `@Scheduled` 方法启动一个虚拟线程，线程内自循环：执行任务体 → 等待下一次触发点 → 再执行。
- `fixedDelay` 在**任务体真正结束后**才开始计时，确保前后两次执行绝不重叠；`fixedRate` 以计划起始点对齐，单线程模型天然不会并发执行同一任务。
- 任务体抛出的异常被记录为 WARNING 后循环继续，不会中断后续调度；非法 cron 或不可达表达式在注册期即跳过并告警，不影响其它任务。
- 在 `SummerApplication.run()` 中于服务器启动后调度，JVM 关闭钩子中调用 `scheduler.shutdown()` 中断所有调度线程并停止。
