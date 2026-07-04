# SLF4J 绑定（@Slf4j 支持）

Summer 自带一个轻量 SLF4J 2.x 绑定，位于 `summer-core` 的 `cn.jiebaba.summer.core.logging.slf4j` 包。它把所有 SLF4J 调用转发到 `java.util.logging`（JUL），因此 Lombok `@Slf4j` 生成的日志、以及直接使用 `org.slf4j.Logger` 的日志，都会统一走 Summer 的日志管道。

## 设计要点

- **不引入任何桥接 jar**：不依赖 `slf4j-jdk14`、`jul-to-slf4j` 等绑定/桥接依赖，绑定代码由 Summer 自身实现。
- **自动发现**：通过标准 `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 由 SLF4J 的 `ServiceLoader` 自动加载，`LoggingInitializer` 无需任何改动。
- **统一管道**：所有 SLF4J 日志最终进入 JUL，受 `logging.level.*`、控制台/文件 handler、`SingleLineFormatter` 统一控制。

## 依赖引入

`summer-core` 将 `org.slf4j:slf4j-api` 声明为 `optional`（版本由父 pom 的 `<slf4j.version>` 统一管理，不硬编码）。需要使用 `@Slf4j` 的应用在自身模块显式引入 slf4j-api 即可：

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
```

配合 Lombok（编译期）：

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

## 使用示例

```java
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 方式一：Lombok @Slf4j（推荐）
@Slf4j
public class OrderService {
    public void create(String orderNo) {
        log.info("create order {}", orderNo);
    }
}

// 方式二：直接使用 SLF4J（无需 Lombok）
public class PriceService {
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
}
```

## 级别映射

| SLF4J | java.util.logging |
| --- | --- |
| TRACE | FINER |
| DEBUG | FINE |
| INFO | INFO |
| WARN | WARNING |
| ERROR | SEVERE |

## 注意事项

- 只能存在一个 SLF4J 绑定：请勿同时引入 logback / slf4j-simple / slf4j-jdk14 等，否则 SLF4J 会告警并二选一，可能导致日志分流。
- 带异常的日志 `log.error("失败 {}", id, e)`：最后一个未消费的 `Throwable` 参数会作为异常挂到 JUL `LogRecord`，`SingleLineFormatter` 会打印其堆栈。
- 包级别过滤对 SLF4J 日志同样生效，例如 `logging.level.cn.jiebaba.summer.sample=DEBUG`。