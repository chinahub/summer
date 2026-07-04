# 技术栈

## 核心选型

| 方面 | 选型 | 理由 |
| --- | --- | --- |
| 语言/JDK | JDK 25（LTS，最低要求 JDK 25） | 虚拟线程稳定、record/模式匹配成熟 |
| HTTP 服务器 | `java.net.ServerSocket` + 手写 HTTP/1.1 | 纯 JDK、无 NIO selector 依赖、契合虚拟线程阻塞 IO 模式 |
| 协程/多线程 | `Executors.newVirtualThreadPerTaskExecutor()` | Loom 虚拟线程，每个连接一个虚拟线程，海量并发、阻塞友好 |
| JSON | 纯 JDK 反射手写 | 支持 record/JavaBean/集合/数组/泛型，零第三方依赖 |
| 日志 | `java.util.logging`（JUL） | JDK 内置，自研 `DailyRollingFileHandler` 按天/按大小滚动；自带 SLF4J→JUL 绑定支持 Lombok `@Slf4j`（见[日志方案](../使用文档/logging.md)、[SLF4J 绑定](../使用文档/logging-slf4j.md)） |
| 配置 | `application.yml` / `.properties` | 自研 `YamlParser`，YML 优先，支持 `${key:default}` 占位符 |
| ORM | 纯 JDBC（`java.sql`） | MyBatis-Plus 风格 BaseMapper/Wrapper/分页/IService，零第三方依赖 |
| SQL 方言 | `Dialect.of(name)` | MySQL/PostgreSQL/Oracle/SqlServer 多方言分页 |
| 事务 | ThreadLocal 连接栈 | 声明式 `@Transactional`，嵌套加入，与 AOP 拦截器链集成 |
| AOP | JDK 动态代理 + 拦截器链 | `@Aspect` + `execution()` 切点，环绕/前置/后置通知 |
| 定时任务 | `ScheduledThreadPoolExecutor` + 虚拟线程 | `@Scheduled`：cron 5 段 + fixedRate/fixedDelay |
| 参数校验 | 手写 Bean Validation 子集 | `@Valid` + 约束注解，递归校验，400 违规列表 |
| 工具集（utils） | 纯 JDK 手写 | `StringUtil`/`DateUtil`/`JsonUtil`/`SecurityUtil`/`SummerUtil`，参考 commons-lang3 与 hutool，详见 [工具集](../使用文档/utils.md) |
| 构建 | Maven（pom modelVersion 4.0.0） | 多模块；离线模式 |
| 测试 | 进程内冒烟测试（`SmokeTest`/`OrmSmokeTest`/`DbSmokeTest`） | 沙箱限制进程间 loopback，用同进程自验证全链路 |

## 零第三方依赖原则

- 框架核心（core/web/data/boot）运行期**不依赖任何第三方库**，只 `requires` JDK 模块（`java.base`、`java.logging`、`java.sql`）；
- HTTP 服务器用 `java.net.ServerSocket` + 阻塞 IO 自研，不开 `com.sun.net.httpserver`（其 NIO selector 的 loopback 管道在受限沙箱里被阻断）；
- 反射、注解处理、字节码读取均用 JDK 内置 API；
- 组件扫描直接读类路径的 `.class`/`.jar` 文件；
- 唯一运行期外部依赖是 **JDBC 驱动**（由使用者自备，如 `postgresql`、`mysql-connector-j`）。
- SLF4J 绑定为**可选**：`summer-core` 以 `optional` 引入 `slf4j-api`，仅当使用方显式引入时才由 SLF4J `ServiceLoader` 激活，框架自身运行期仍是零第三方依赖。

## 为什么不用 Servlet

- Servlet 规范（`jakarta.servlet`）会引入第三方 API 与容器概念；
- 需求明确：用 JDK 内置库实现服务器。

## 为什么不用 com.sun.net.httpserver

- 实测 `HttpServer.create()` 会创建 NIO `Selector`，其底层在 Windows 上用 Unix-domain-socket 管道（loopback），在受限沙箱里被阻断（与 mvnd 守护进程同一问题）；
- 改用 `ServerSocket` + 虚拟线程后，既能跑通，又更契合 Loom 的「每连接一虚拟线程 + 阻塞 IO」范式，且更「自研」。

## 虚拟线程 = 协程

JDK 21 起虚拟线程正式发布，JDK 25 为 LTS。`newVirtualThreadPerTaskExecutor()` 让每个 HTTP 连接在自己的虚拟线程上执行：

- 阻塞 IO（读 body、业务处理）不占平台线程；
- 无需手写异步回退，代码风格仍是同步阻塞；
- 对应 Spring Boot 中「每请求一线程」模型，但线程成本从 MB 级降到 KB 级。