# summer 框架文档

> summer —— 基于 JDK 25 内置库、虚拟线程协程构建的类 Spring Boot 微服务框架，不使用 Servlet 规范，尽量不依赖第三方库。

## 🚀 快速开始

| 文档 | 内容 |
| --- | --- |
| [安装指南](使用文档/installation.md) | 环境准备、Maven 配置、编码注意事项 |
| [使用手册](使用文档/usage.md) | 构建/运行、配置（YML/properties）、注解速查、工具集 |

## 🏗 开发文档

项目设计、架构、技术选型与开发计划。

| 文档 | 内容 |
| --- | --- |
| [技术栈](开发文档/tech-stack.md) | JDK 25、虚拟线程、ServerSocketChannel(NIMA)、TLS、JUL、Maven |
| [架构设计](开发文档/architecture.md) | 模块划分、依赖关系、运行时模型、启动流程 |
| [发布到 Maven Central](开发文档/publishing.md) | release profile、GPG 签名、Central Portal 上传 |
| [高级特性研究](开发文档/research-advanced.md) | WebSocket/异步控制器/连接池增强/自研子类代理 |
| [开发路线图](开发文档/roadmap.md) | 分阶段开发计划与后续扩展项 |

## 📖 使用文档

按功能领域组织的使用指南。

### 核心能力

| 文档 | 内容 |
| --- | --- |
| [AOP](使用文档/aop.md) | `@Aspect` 切面、`execution()` 切点、环绕/前置/后置通知、自研字节码代理 |
| [定时任务](使用文档/scheduling.md) | `@Scheduled`：cron 表达式 + fixedRate/fixedDelay，虚拟线程执行 |
| [参数校验](使用文档/validation.md) | `@Valid` + 约束注解，递归校验，400 违规列表 |
| [ApplicationRunner](使用文档/application-runner.md) | 启动就绪回调：缓存预热/字典加载，按 `@Order` 排序 |
| [工具集](使用文档/utils.md) | StringUtil/DateUtil/JsonUtil/SecurityUtil/SummerUtil |

### 数据访问

| 文档 | 内容 |
| --- | --- |
| [ORM](使用文档/orm.md) | MyBatis-Plus 风格：BaseMapper/Wrapper/分页/IService/事务/多方言 |
| [多数据源](使用文档/multi-datasource.md) | @DS/@Master/@Slave + @DSTransactional 跨源事务 |

### Web 服务

| 文档 | 内容 |
| --- | --- |
| [WebSocket](使用文档/websocket.md) | `@WebSocketEndpoint`，纯 JDK 握手+帧协议（RFC 6455） |
| [SSE](使用文档/sse.md) | 服务端事件推送：`SseEmitter` + `SseEvent` + `SseHub` 广播中枢，chunked 分块，LLM 流式转发 |
| [4.0 增强速览](使用文档/whats-new-4.0.md) | JSON 前端安全序列化、SseHub、异步事件、乐观锁/批量/upsert/迁移、AI 装配解耦与用量计量、静态资源 |
| [文件上传](使用文档/multipart.md) | `multipart/form-data` 解析、`@RequestPart` + `MultipartFile` |
| [CORS](使用文档/cors.md) | 跨域配置 |

### 安全与认证

| 文档 | 内容 |
| --- | --- |
| [安全](使用文档/security.md) | JWT 无状态认证、BCrypt、URL/方法级授权、HttpSecurity DSL |

### AI 与智能

| 文档 | 内容 |
| --- | --- |
| [AI 对话](使用文档/ai.md) | ChatClient、OpenAI 兼容、SSE 流式、工具调用、RAG、本地 ONNX Embedding |
| [OCR](使用文档/ocr.md) | ONNX 引擎 OCR：文本检测 + 识别 + 分类 |
| [模型下载](使用文档/model-assets.md) | onnxruntime 原生库、PP-OCR、BGE-M3 模型下载指南 |

### 日志与监控

| 文档 | 内容 |
| --- | --- |
| [日志方案](使用文档/logging.md) | JUL 滚动实测 + 双通道日志（控制台+文件按天/按大小滚动） |
| [SLF4J 绑定](使用文档/logging-slf4j.md) | 自研 SLF4J→JUL 绑定，支持 Lombok `@Slf4j` |

## 🤖 AI Agent 使用

本项目同时面向人类开发者和 AI 编程助手（Claude Code、Codex 等）优化。

| 位置 | 用途 | 加载时机 |
| --- | --- | --- |
| [AGENTS.md](../AGENTS.md) | 项目宪法：构建命令、模块速览、编码规范 | 每次会话 |
| [各模块 AGENTS.md](../summer-boot/AGENTS.md) | 模块专属约定（summer-boot / summer-support / summer-ai 等） | 仅当触碰该模块时 |
| [.claude/skills/](../.claude/skills/) | 工作流：代码审查、发布、部署 | 调用时加载 |
| [.claude/rules/](../.claude/rules/) | 路径限定规则：Java 编码、配置文件、模块依赖 | 仅当触碰匹配文件时 |

> 设计理念参考 [Anthropic 官方：七种自定义方式决策框架](https://www.toutiao.com/article/7653321298067718671)

## 📦 模块一览

```
summer-parent (pom)
├── summer-core              核心运行时（cn.jiebaba.summer.core）：IoC/AOP/日志/扫描/定时/配置环境/JSON/工具集/ONNX 引擎/测试微框架
├── summer-boot            启动器 + 自动配置（依赖 summer-core）：SummerApplication.run()/自动配置/嵌入式HTTP(NIMA)/ORM/事务/JWT 安全/xlsx/docx/pdf/csv/md/xml 文档读写，关闭钩子
├── summer-support         OCR 文字识别：DB 检测 + 方向分类 + CRNN 识别流水线（FFM 直连 onnxruntime 原生库），依赖 summer-core
├── summer-ai              大模型对话抽象：ChatModel/ChatClient，OpenAI 兼容，同步与 SSE 流式，纯 JDK；AI 自动配置（cn.jiebaba.summer.boot.ai 包）随本模块发布，依赖 summer-boot
├── summer-boot-loader     可执行 jar 启动器 JarLauncher（java -jar 入口），由插件内置打包
├── summer-pack-maven-plugin  mvn package 自动产出 BOOT-INF 可执行 jar
├── summer-sample          示例应用（Application + controller/service/repository/aspect）
└── build-test              集中式测试：AOP 单测/集成测试 + sample 冒烟测试
```

> summer-boot 不编译期依赖 summer-ai / summer-support（避免循环依赖），二者由 SummerApplication
> 按 classpath 探测（`isClassPresent`）+ `Class.forName` 反射注册自动配置。
> summer-support 仅依赖 summer-core（不依赖 summer-boot），OCR 为可选模块。

## 一句话定位

用 JDK 25 的 `ServerSocketChannel`（阻塞，参考 Helidon NIMA）做嵌入式 HTTP 服务器（支持 TLS 与 chunked 请求体），用 `Executors.newVirtualThreadPerTaskExecutor()` 把每个请求跑在虚拟线程（协程）上，通过 `java -jar` 运行——全程不开 Servlet、尽量不引第三方库；并内置 MyBatis-Plus 风格的 JDBC ORM、AOP、声明式事务、定时任务与参数校验，以及 JWT 安全与国内大模型对话抽象。
