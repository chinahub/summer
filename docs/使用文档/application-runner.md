# ApplicationRunner（summer-boot）

在应用上下文完全启动、Web 端口已监听之后，自动执行初始化逻辑——对标 Spring Boot 的 `ApplicationRunner`。

## 作用

实现 `ApplicationRunner` 接口的 Bean，会在 `SummerApplication.run()` 完成以下步骤后被调用：

1. 所有单例 Bean 加载、依赖注入、`@PostConstruct`/`InitializingBean` 初始化完成；
2. Web Server 已 `start()`、端口开始监听；
3. 定时任务已注册、关闭钩子已挂载。

典型用途：缓存预热、启动时加载字典数据到内存、启动时打印 Banner、启动时检查外部服务是否可用。

## 执行顺序

存在多个 Runner 时，按 `@Order` 升序执行（值小的先跑）；未标注 `@Order` 的 Runner 排在最后，并保持发现顺序（稳定排序）。

## 用法

定义一个 `@Component` 并实现 `ApplicationRunner`：

```java
package cn.jiebaba.summer.sample.runner;

import cn.jiebaba.summer.boot.ApplicationArguments;
import cn.jiebaba.summer.boot.ApplicationRunner;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.Order;

import java.util.logging.Logger;

@Component
@Order(1)
public class StartupRunner implements ApplicationRunner {

    private static final Logger LOG = Logger.getLogger(StartupRunner.class.getName());

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LOG.info("application ready, warming up caches / loading dictionaries...");
        if (args.containsOption("verbose")) {
            LOG.info("verbose mode, non-option args=" + args.getNonOptionArgs());
        }
    }
}
```

无需任何额外配置：只要该类在 `@SummerBootApplication` 的扫描包内，框架会自动发现并执行。

## ApplicationArguments

`run(ApplicationArguments args)` 的参数封装了启动参数，采用 Spring Boot 风格的 `--` 约定：

| 方法 | 说明 |
|------|------|
| `getSourceArgs()` | 原始参数数组 |
| `getOptionNames()` | 所有选项名（`--name` / `--name=value` 中 `--` 与 `=` 之间的部分） |
| `containsOption(name)` | 是否存在该选项 |
| `getOptionValues(name)` | 该选项的值列表；`--name` 无 `=` 时为空列表；可多次出现 `--k=1 --k=2` |
| `getNonOptionArgs()` | 非 `--` 前缀的参数 |

示例：启动参数 `--name=summer --debug positional` 解析为

- `containsOption("name")` → `true`，`getOptionValues("name")` → `["summer"]`
- `containsOption("debug")` → `true`，`getOptionValues("debug")` → `[]`
- `getNonOptionArgs()` → `["positional"]`

## 失败处理

任一 Runner 抛出异常时，框架将其包装为 `IllegalStateException` 抛出，**后续 Runner 不再执行**，应用启动中止。因此重初始化逻辑应做好容错：可检查的外部服务异常应捕获并记录日志，避免拖垮启动。

## 与 @PostConstruct / InitializingBean 的区别

| 维度 | `@PostConstruct` / `InitializingBean` | `ApplicationRunner` |
|------|------|------|
| 触发时机 | 单个 Bean 注入完成时 | 所有 Bean 就绪 + Web 端口监听后 |
| 可访问完整上下文 | 否（其他 Bean 可能尚未创建） | 是 |
| 能确认端口已监听 | 否 | 是 |
| 执行顺序控制 | 无 | 按 `@Order` |

需要"整个应用就绪后"才能可靠完成的工作（如访问其他 Bean、依赖端口已监听），应使用 `ApplicationRunner`；仅依赖自身注入属性的局部初始化，用 `@PostConstruct` 即可。

## 手动触发

`invokeRunners` 也暴露为公共静态方法，可在已刷新的上下文上手动调用（主要用于测试）：

```java
SummerApplication.invokeRunners(context, args);
```