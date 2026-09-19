# 技术栈

## 核心选型

| 方面 | 选型 | 理由 |
| --- | --- | --- |
| 语言/JDK | JDK 25（LTS，最低要求 JDK 25） | 虚拟线程稳定、record/模式匹配成熟 |
| HTTP 服务器 | `ServerSocketChannel`（阻塞）+ 手写 HTTP/1.1（参考 Helidon NIMA） | 虚拟线程/连接 + 阻塞 NIO 通道；支持 TLS、`Transfer-Encoding: chunked` 请求体、聚集写；无 selector、无第三方依赖 |
| 协程/多线程 | `Executors.newVirtualThreadPerTaskExecutor()` | Loom 虚拟线程，每个连接一个虚拟线程，海量并发、阻塞友好 |
| JSON | 纯 JDK 反射手写 | 支持 record/JavaBean/集合/数组/泛型，零第三方依赖 |
| 日志 | `java.util.logging`（JUL） | JDK 内置，自研 `DailyRollingFileHandler` 按天/按大小滚动；自带 SLF4J→JUL 绑定支持 Lombok `@Slf4j`（见[日志方案](../使用文档/logging.md)、[SLF4J 绑定](../使用文档/logging-slf4j.md)） |
| 配置 | `application.yml` / `.properties` | 自研 `YamlParser`（properties 同名键优先，支持 `---` 多文档）；`summer.profiles.active` 多 profile 叠加（`application-{profile}.yml`）；环境变量按需匹配；命令行 `--key=value` 最高优先；`${key:default}` 占位符 |
| ORM | 纯 JDBC（`java.sql`） | MyBatis-Plus 风格 BaseMapper/Wrapper/分页/IService，零第三方依赖 |
| SQL 方言 | `Dialect.of(name)` | MySQL/PostgreSQL/Oracle/SqlServer 多方言分页 |
| 事务 | ThreadLocal 连接栈 | 声明式 `@Transactional`，嵌套加入，与 AOP 拦截器链集成 |
| AOP | JDK 动态代理 + 拦截器链 | `@Aspect` + `execution()` 切点，环绕/前置/后置通知 |
| 定时任务 | `ScheduledThreadPoolExecutor` + 虚拟线程 | `@Scheduled`：cron 5 段 + fixedRate/fixedDelay |
| 参数校验 | 手写 Bean Validation 子集 | `@Valid` + 约束注解，递归校验，400 违规列表 |
| 工具集（utils） | 纯 JDK 手写 | `StringUtil`/`DateUtil`/`JsonUtil`/`SecurityUtil`/`SummerUtil`，参考 commons-lang3 与 hutool，详见 [工具集](../使用文档/utils.md) |
| 大模型对话（AI） | 纯 JDK `HttpURLConnection` + 自研 `ChatModel`/`ChatClient` | OpenAI 兼容协议直连 DeepSeek/GLM/MiniMax/Kimi；阻塞式无 selector，支持 SSE 流式与思维链；依赖 summer-boot（复用其 core 的 `JsonUtil` 等能力），零第三方依赖，详见 [AI 对话](../使用文档/ai.md) |
| 文档处理（Office） | 纯 JDK 自研（xlsx SAX/ZipOutputStream、docx ZipFile+StAX、pdf 直接生成/解析对象结构） | 自研 `OfficeReader`/`OfficeWriter`/`TableReader`/`TableWriter` 抽象；csv/md/xml 零第三方依赖；xlsx/docx 用 FastExcel（Apache-2.0），pdf 用 iText 7（AGPL-3.0），均 optional 按 classpath 探测条件激活；`Excel` fluent API 支持 TableData 与 Bean 双模式，详见 [路线图](roadmap.md) 第九阶段 |
| 构建 | Maven（pom modelVersion 4.0.0） | 多模块；离线模式 |
| 测试 | JUnit 5（Jupiter）+ 自研 `@SummerTest`/`SummerExtension` 整合层 | 参照 Spring Boot Test/Helidon/Quarkus 的 `@ExtendWith` 整合形态：用例写真实 `org.junit.jupiter.api.Test`，IDEA 绿色三角与 surefire 原生执行；`SmokeTest`/`OrmSmokeTest`/`DbSmokeTest` 等进程内冒烟测试保留（沙箱限制进程间 loopback，用同进程自验证全链路），详见下文「为什么不走 junit-like」 |

## 零第三方依赖原则

- 框架核心（core/web/data/security/ai/boot）运行期**不依赖任何第三方库**，只 `requires` JDK 模块（`java.base`、`java.logging`、`java.sql`）；
- HTTP 服务器用 `ServerSocketChannel`（阻塞模式）+ NIO 通道自研，不开 `com.sun.net.httpserver`（其 NIO selector 的 loopback 管道在受限沙箱里被阻断）；TLS 直接用 JDK `SSLContext`/`SSLSocket`，无第三方加密库；
- 反射、注解处理、字节码读取均用 JDK 内置 API；
- 组件扫描直接读类路径的 `.class`/`.jar` 文件；
- 唯一运行期外部依赖是 **JDBC 驱动**（由使用者自备，如 `postgresql`、`mysql-connector-j`）。
- SLF4J 绑定为**可选**：`summer-boot`（原 summer-core）以 `optional` 引入 `slf4j-api`，仅当使用方显式引入时才由 SLF4J `ServiceLoader` 激活，框架自身运行期仍是零第三方依赖。
- **summer-ai** 同样零第三方依赖：仅依赖 summer-boot（用其 core/data 能力）；其自动配置类随本模块发布，由 summer-boot 启动时按 classpath 探测反射激活（详见 [AI 对话](../使用文档/ai.md)）。
- **summer-office** 已并入 summer-boot：xlsx/docx/pdf 均为自研实现（SAX/ZipOutputStream/StAX/直接生成 PDF 对象结构），零第三方依赖；OCR 独立为 summer-support 模块
- **测试侧 JUnit 5 不进运行期**：`summer-boot` 仅以 `optional` 引入 `junit-jupiter-api`（供 `@SummerTest`/`SummerExtension` 整合层编译）；`junit-jupiter`、`junit-platform-launcher` 只在 summer-sample（test scope）与 build-test（不发布模块）中使用，均不进入框架运行期的传递依赖。

## 为什么不走 junit-like（自研测试框架）

曾经路线：`cn.jiebaba.summer.core.test` 自研 junit-like 微框架（`@Test`/`@BeforeEach`/`Assert`/`Assumptions` + `TestRunner` 反射扫描执行，build-test 用 exec-maven-plugin 在 test 阶段触发）。3.0.0 起废弃并删除，原因：

- **IDE 不识别**：IDEA 的测试绿色三角/测试树由 JUnit/TestNG 插件按全限定名硬编码识别（`org.junit.jupiter.api.Test` 等），自研注解永远拿不到 IDE 集成，只能命令行执行；
- **生态不可复用**：进不了 surefire/IDE/CI 的 JUnit Platform 体系（XML 报告、失败跳转、覆盖率统计、方法级执行）；
- **缩水复刻、维护成本高**：参数化、显示名、假设跳过、超时、扩展模型等都要自己重写一遍，本质是把 JUnit 5 重新发明一遍但更差；
- **冒名方案治标不治本**：曾评估把注解包名改成 `org.junit.jupiter.api.Test` 骗取 IDE 识别——按钮能显示，但点击执行时 IDE 走真实 JUnit Platform，classpath 没有 launcher/engine 类照样失败；且伪造类与真 JUnit 共存即类冲突，还会污染发布构件。

**真正路线**（参照 Spring Boot Test 的 `@SpringBootTest`+`SpringExtension`、Quarkus 的 `@QuarkusTest`+`QuarkusTestExtension`、Helidon Testing 的 `@HelidonTest`）：

- 测试用例写真实的 `org.junit.jupiter.api.Test`，跑在真实 Jupiter 引擎上，IDEA 绿色三角与 `mvn test`（surefire）开箱即用；
- 框架侧只做**整合层**而非执行引擎：`@SummerTest`（元注解 `@ExtendWith(SummerExtension.class)`）+ `SummerExtension`（每测试类启动 `DefaultApplicationContext`，支持 `@Autowired` 字段注入与 bean 类型参数注入），位于 `cn.jiebaba.summer.core.test`；
- 旧框架 12 个类（`Test`/`TestRunner`/`Assert` 等）全部删除——JUnit 5 同名能力全覆盖；build-test 46 个用例已机械迁移（`Assert.`→`Assertions.` 等），`@Test(expected=)` 改写为 `assertThrows`；
- 执行方式：summer-sample 走标准 `src/test` + surefire；build-test 用例在 `src/main`（非常规），surefire 配 `testClassesDirectory=${project.build.outputDirectory}` 扫描执行，exec-maven-plugin 已移除；
- 配套修复：`ClassPathScanner` 除 `java.class.path` 外并入 `URLClassLoader` 的 URL 作为扫描根，覆盖 surefire 进程内隔离类加载器环境。

## 为什么不用 Servlet

- Servlet 规范（`jakarta.servlet`）会引入第三方 API 与容器概念；
- 需求明确：用 JDK 内置库实现服务器。

## 为什么不用 com.sun.net.httpserver

- 实测 `HttpServer.create()` 会创建 NIO `Selector`，其底层在 Windows 上用 Unix-domain-socket 管道（loopback），在受限沙箱里被阻断（与 mvnd 守护进程同一问题）；
- 改用 `ServerSocketChannel`（阻塞模式）+ 虚拟线程后，既能跑通，又更契合 Loom 的「每连接一虚拟线程 + 阻塞 IO」范式，且更「自研」；阻塞通道不涉及 selector，规避了上述 loopback 管道问题。

## 虚拟线程 = 协程

JDK 21 起虚拟线程正式发布，JDK 25 为 LTS。`newVirtualThreadPerTaskExecutor()` 让每个 HTTP 连接在自己的虚拟线程上执行：

- 阻塞 IO（读 body、业务处理）不占平台线程；
- 无需手写异步回退，代码风格仍是同步阻塞；
- 对应 Spring Boot 中「每请求一线程」模型，但线程成本从 MB 级降到 KB 级。

## Web 服务器架构（参考 Helidon NIMA）

底层服务器参考 Helidon Níma 的设计思路：**虚拟线程/连接 + 阻塞 NIO 通道**，不用 selector、不用响应式回压——阻塞读取会自动卸载载体线程，代码仍是同步阻塞风格。

- **连接生命周期**：`ServerSocketChannel`（阻塞）在平台线程上 `accept` → 每连接提交一个虚拟线程 `handleConnection` → 可选 `wrapTls` 叠加 TLS → 连接级 `ByteBuffer` 循环「读请求 → 分发 → 写响应」，keep-alive 复用同一连接与缓冲。
- **idle 超时**：单线程 `idleScheduler` 调度 `Thread.interrupt()` 中断虚拟线程的阻塞读（替代 `Socket.setSoTimeout`），以 `ClosedByInterruptException` 体现，读超时即关闭连接。
- **TLS/SSL**（可选，配置 `server.ssl.*`）：`buildSslContext` 加载 keystore（服务端证书）与可选 truststore 建 `SSLContext`；`wrapTls` 以 `SSLSocket`（服务端模式、可选 `needClientAuth` 双向认证、`startHandshake`）包底层 Socket，`SslByteChannel` 将其适配为 `ByteChannel`，使上层 HTTP 解析与响应写入**透明工作于 TLS**。
  - 配置项：`server.ssl.enabled` / `keystore` / `keystoretype` / `keystorepassword` / `truststore` / `truststoretype` / `truststorepassword` / `needclientauth`。
- **请求体**：`RawHttpRequest` 支持 `Content-Length` 与 `Transfer-Encoding: chunked`（chunk-size 行 + trailer 解码）两种定界；提供 `InputStream` 与 `ReadableByteChannel+ByteBuffer` 两个解析入口；连接级 `ByteBuffer` 跨 keep-alive 请求与 WebSocket 升级复用残留字节。
- **响应体**：`WebResponse` 写 `WritableByteChannel`；当 sink 为 `GatheringByteChannel`（生产环境的 `SocketChannel`）时以**聚集写（vectored write）**合并状态行+头+体一次发出，减少系统调用；响应仍以 `Content-Length` 定长（响应体缓冲为 `byte[]`）。
- **零依赖**：仅用 JDK（`java.nio.channels`、`javax.net.ssl`），不引入 Netty/Reactor 或第三方加密库。
