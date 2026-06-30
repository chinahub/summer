# 日志方案

框架自身用 `java.util.logging`（JUL），零第三方依赖。`LoggingInitializer` 在启动早期根据配置装配双通道：控制台 + 文件滚动。

## 配置

日志配置写在 `application.yml`（或 `application.properties`）：

```yaml
logging:
  level:
    root: INFO
    cn.jiebaba.summer: INFO
  console:
    enabled: true
  file:
    enabled: true
    path: logs
    name: summer
    rolling-policy: time        # time 或 size-time
    max-size: 10MB
    max-history: 7
  format:
    single-line: true
```

| 配置项 | 说明 |
| --- | --- |
| `logging.level.root` / `logging.level.<包>` | 全局/包级日志级别 |
| `logging.console.enabled` | 是否开启控制台输出 |
| `logging.file.enabled` | 是否开启文件输出 |
| `logging.file.path` | 日志目录 |
| `logging.file.name` | 文件名前缀（生成 `name.yyyy-MM-dd.log`） |
| `logging.file.rolling-policy` | `time`（按天）或 `size-time`（按天+大小） |
| `logging.file.max-size` | 单文件上限（`size-time` 时触发分段） |
| `logging.file.max-history` | 历史保留天数（过期自动清理） |
| `logging.format.single-line` | 单行格式 |

> JUL 原生 `FileHandler` 支持按大小滚动，但**不支持按天滚动**。summer 自研 `DailyRollingFileHandler` 补齐按天/按天+大小双滚动，并支持历史清理。

## 滚动文件

- **time**：每天一个文件 `summer.yyyy-MM-dd.log`，跨天自动滚动；
- **size-time**：当天文件达到 `max-size` 后分段为 `summer.yyyy-MM-dd.1.log`、`.2.log`…，跨天仍按天滚动；
- 超过 `max-history` 的旧文件自动删除。

## 单行格式

`SingleLineFormatter` 把每条日志压成一行（含时间、级别、线程、logger、消息），便于采集与检索。

## 关闭钩子

框架注册 JVM 关闭钩子优雅停止服务器与定时任务。`DailyRollingFileHandler.publish` 对 `out==null` 做了防御（`LogManager` 关闭钩子与框架钩子并发时 handler 可能已被关闭），避免关闭期 NPE。
