# summer-boot 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-boot/` 目录下的文件时才会加载本文件。

## 模块职责

启动器 + 自动配置。本模块整合原 summer-web（嵌入式 HTTP + 路由 + 校验 + WebSocket）、summer-data（ORM + 事务 + 多数据源）、summer-security（JWT 认证授权 + BCrypt + CSRF）、summer-office（xlsx/docx/pdf/csv/md/xml 文档读写，OCR 移至 summer-support）。核心运行时（IoC/DI/扫描/配置 + 日志 + AOP + 定时任务 + 工具集 + ONNX 引擎，即 `cn.jiebaba.summer.core.*` 包）已拆至 summer-core 模块，本模块经 Maven 依赖 summer-core 获得这些能力（包名不变）。`SummerApplication.run()` 是应用唯一入口。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summer.boot` | 启动：SummerApplication/ApplicationRunner/ApplicationArguments/@SummerBootApplication |
| `cn.jiebaba.summer.boot.web` | Web 自动配置（WebAutoConfiguration） |
| `cn.jiebaba.summer.boot.data` | 数据源 + Mapper 自动配置（DataAutoConfiguration/MapperRegistrar） |
| `cn.jiebaba.summer.boot.security` | 安全自动配置（SecurityAutoConfiguration） |
| `cn.jiebaba.summer.boot.office` | Office 自动配置（OfficeAutoConfiguration，office 已并入本模块，无条件注册） |
| `cn.jiebaba.summer.web` | HTTP 服务器（server，ServerSocketChannel + 虚拟线程 NIMA）、路由（routing）、请求绑定（bind）、HTTP 抽象（http）、消息转换（convert）、参数校验（validation）、WebSocket（websocket）、文件上传（multipart）、CORS（cors）、SSE（sse）、过滤器链（filter）、路由/异常注册（support）、Web 注解（annotation） |
| `cn.jiebaba.summer.data` | Mapper（mapper）、条件构造（conditions）、分页（page）、Service（service）、底层支撑（support）、事务（transaction）、多数据源（datasource）、元数据（metadata）、多方言（dialect）、注解（annotation） |
| `cn.jiebaba.summer.security` | Web 安全 + JWT 过滤器（web）、JWT 自研实现（jwt）、BCrypt 密码编码（crypto）、认证（authentication）、授权（authorization）、用户详情（userdetails）、安全上下文（core）、CSRF（web.csrf）、注解（annotation） |
| `cn.jiebaba.summer.office` | Office/OfficeReader/OfficeWriter 门面 + TableData 表格抽象；xlsx（excel）、docx（docx）、pdf（pdf）、md（md）、csv（csv）、xml（xml）读写器 |

## 模块约定

- **启动入口**：`SummerApplication.run(MainClass.class, args)`——唯一启动方式
- **依赖 summer-core**：IoC/AOP/日志/扫描/定时/env/json/util/onnx/test 均在 summer-core 模块（包名 `cn.jiebaba.summer.core` 不变），本模块 POM 显式依赖 summer-core 并传递给使用者
- **零第三方运行时依赖**：仅 SLF4J API 与 JUnit API 为 optional 依赖（SLF4J→JUL 绑定与 @SummerTest 测试微框架，均已随 core 拆分迁至 summer-core）
- **自动配置**：Data/Security/Web/Office 由 `SummerApplication.AUTO_CONFIG_CLASSES` 硬编码无条件注册；
  summer-ai 与 summer-support（OCR）为可选模块，以 classpath 存在性探测（`isClassPresent`，等价 @ConditionalOnClass）
  + `Class.forName` 反射注册（summer-boot 不编译期依赖二者，避免循环依赖；AI 自动配置类在 summer-ai 模块内，
  包名仍为 `cn.jiebaba.summer.boot.ai`）；第三方模块可通过 classpath 根 `META-INF/summer.factories`
  声明 `@Configuration` 类名加入（每行一个，兼容 `key=value` 格式，缺失类跳过）
- **AOP 代理**：使用自研字节码引擎（`core/aop/bytecode/`，ConstantPool/ClassBuilder/MethodBuilder/Descriptor），不依赖 CGLIB 或 ByteBuddy；接口用 JDK 动态代理，无接口类用于类代理
- **日志**：底层使用 JUL（`java.util.logging`，DailyRollingFileHandler/SingleLineFormatter），提供自研 SLF4J→JUL 绑定和 MDC 适配器（META-INF/services/org.slf4j.spi.SLF4JServiceProvider）
- **配置**：YAML（`application.yml`，含 `---` 多文档与 `summer.config.activate.on-profile` 条件段）和 Properties 双格式；`summer.profiles.active` 激活多 profile 叠加 `application-{profile}.*`；环境变量按需匹配（`SERVER_PORT`→`server.port`），系统属性与命令行 `--key=value` 优先级更高；`@ConfigurationProperties` 按前缀绑定字段（kebab-case，支持 List/Map/嵌套 POJO）
- **事件**：`@EventListener` 同步发布（类型可赋值匹配，监听器惰性创建）；内置 ContextRefreshedEvent/ContextClosedEvent；refresh 完成发布 ContextRefreshedEvent，Runner 执行后发布 ApplicationReadyEvent，启动失败发布 ApplicationFailedEvent
- **Web（NIMA 模型）**：参考 Helidon NIMA，阻塞式 ServerSocketChannel + 虚拟线程，每请求一个虚拟线程；Router 支持 path variable 不支持正则；Validator 递归校验嵌套对象失败返回 400；gzip 按 `server.compression.*` 判定回退明文；SSE 返回 SseEmitter 即开启事件流；关键接口（WebRequest/WebResponse/MessageConverter/ArgumentResolver）针对接口编程
- **Data（纯 JDBC）**：不依赖 MyBatis/Hibernate；`LambdaQueryWrapper` 用 `SFunction` 类型安全条件构造；`MapperProxyFactory` JDK 动态代理转 SQL；`Dialect` 多方言适配分页（MySQL/PG/Oracle/SQL Server，H2/SQLite 复用 MySQL，未识别回退 PG 并告警）；`Dialect.quote` 仅保留字/非法标识符加引号；Oracle String 走 getString、时间走 getTimestamp、keepalive 用 `SELECT 1 FROM DUAL`、主键仅 IDENTITY；@DS + ThreadLocal 多数据源，支持读写分离（@Master/@Slave）与 @DSTransactional 跨源事务
- **Security（纯 JDK）**：JWT 自研（Base64 + HMAC/RSA），BCrypt 自研，JSON 自研；过滤器链 JwtLoginFilter → JwtAuthenticationFilter → AuthorizationFilter；`MethodSecurityEnforcer` + @PreAuthorize 方法级授权；无状态不用 Session
- **Office（零第三方）**：xlsx（SAX 读取 + ZipOutputStream 写出）、docx（ZipFile + StAX）、pdf（直接生成/解析 PDF 对象结构）均为自研实现，不依赖 POI/PDFBox；csv/md/xml 纯 JDK；所有格式经 `OfficeReader`/`OfficeWriter` 或 `TableReader`/`TableWriter` 统一接口访问
- **条件注解**：`@ConditionalOnMissingBean`（退避注册，最后评估）、`@ConditionalOnProperty`、@ConditionalOnClass
- **启动诊断**：启动失败输出 `APPLICATION FAILED TO START` 报告（阶段/根因/建议）并回收已创建资源
- **关闭钩子**：注册 JVM shutdown hook，优雅关闭（scheduler → webServer → context，各步独立捕获异常）
