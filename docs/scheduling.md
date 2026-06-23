# 定时任务（summer-core）

基于 `ScheduledThreadPoolExecutor`（精确定时）+ 虚拟线程执行器（任务体）实现，零第三方依赖。支持 cron 表达式与固定速率/固定延迟两种模式。

## 注解

`@Scheduled`（可重复，标注在方法上）：

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `cron` | `""` | 5 段 cron：`分 时 日 月 周`（如 `0 */5 * * * *`→每 5 分钟；此处为 5 段 `分 时 日 月 周`） |
| `fixedRate` | `-1` | 固定速率（毫秒）：从上次**开始**到下次**开始**的间隔 |
| `fixedDelay` | `-1` | 固定延迟（毫秒）：从上次**结束**到下次**开始**的间隔 |
| `initialDelay` | `0` | 首次执行前的延迟（毫秒） |

`cron` 与 `fixedRate/fixedDelay` 二选一；同时配置时以 `cron` 优先。

## Cron 表达式

5 个字段，空格分隔：

```
分(0-59)  时(0-23)  日(1-31)  月(1-12)  周(0-6, 0=周日)
```

支持：`*`（任意）、`,`（列表）、`-`（范围）、`/`（步长，如 `*/5`）。例如 `0 9 * * 1-5` 表示工作日每天 9:00。

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

- `ScheduledTaskRegistrar` 用**单线程** `ScheduledThreadPoolExecutor` 负责定时触发（避免任务互相阻塞影响调度精度）；
- 触发后把任务体提交给 `Executors.newVirtualThreadPerTaskExecutor()`，即每个任务执行在独立虚拟线程上，阻塞 IO 不占平台线程；
- 在 `SummerApplication.run()` 中于服务器启动后调度，JVM 关闭钩子中调用 `scheduler.shutdown()` 停止。