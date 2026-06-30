# summer 框架文档

> summer —— 基于 JDK 25 内置库、虚拟线程协程构建的类 Spring Boot 微服务框架，不使用 Servlet 规范，尽量不依赖第三方库。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [技术栈](开发文档/tech-stack.md) | 技术选型：JDK 25、虚拟线程、ServerSocket、JUL、Maven、零第三方依赖 |
| [架构设计](开发文档/architecture.md) | 模块划分、依赖关系、运行时模型、启动流程 |
| [安装](使用文档/installation.md) | 环境准备、纯离线 Maven 配置、编码注意 |
| [使用](使用文档/usage.md) | 构建/运行、配置（YML/properties）、注解速查、工具集、示例、测试结果 |
| [日志方案](使用文档/logging.md) | JUL 滚动实测 + summer 双通道日志（控制台+文件按天/按大小滚动） |
| [数据访问 ORM](使用文档/orm.md) | MyBatis-Plus 风格 ORM：BaseMapper/Wrapper/分页/IService/事务/多方言 |
| [AOP](使用文档/aop.md) | `@Aspect` 切面、`execution()` 切点、环绕/前置/后置通知、JDK 动态代理 + 无接口子类代理 |
| [定时任务](使用文档/scheduling.md) | `@Scheduled`：cron 表达式 + fixedRate/fixedDelay，虚拟线程执行 |
| [参数校验](使用文档/validation.md) | `@Valid` + 约束注解，递归校验，400 违规列表 |
| [工具集](使用文档/utils.md) | StringUtil/DateUtil/JsonUtil/SecurityUtil/SummerUtil：纯 JDK 工具类，参考 commons-lang3 与 hutool |
| [开发路线图](开发文档/roadmap.md) | 分阶段开发计划与后续扩展项 |
| [高级特性研究](开发文档/research-advanced.md) | WebSocket / 异步控制器 / 连接池增强 / 自研子类代理（无 CGLIB 依赖） |

## 模块一览

```
summer-parent (pom)
├── summer-core            IoC/DI/扫描/配置 + 日志 + AOP + 定时任务
├── summer-web             嵌入式 HTTP 服务器(ServerSocket+虚拟线程)/路由/JSON/绑定/异常/校验/WebSocket
├── summer-data            ORM：BaseMapper/Wrapper/分页/IService/事务/多方言/多数据源，纯 JDBC，零第三方依赖
├── summer-boot            SummerApplication.run() 启动器/自动配置/数据源/Mapper装配/关闭钩子
├── summer-loader          可执行 jar 启动器 JarLauncher（java -jar 入口）
├── summer-pack-maven-plugin  mvn package 自动产出 BOOT-INF 可执行 jar
├── summer-sample          示例应用（Application + controller/service/repository/aspect）
└── build-test              集中式测试：AOP 单测/集成测试 + sample 冒烟测试（测试微框架在 summer-core 的 core.test 包）
```

## 一句话定位

用 JDK 25 的 `java.net.ServerSocket` 做嵌入式 HTTP 服务器，用 `Executors.newVirtualThreadPerTaskExecutor()` 把每个请求跑在虚拟线程（协程）上，通过 `java -jar` 运行——全程不开 Servlet、尽量不引第三方库；并内置 MyBatis-Plus 风格的 JDBC ORM、AOP、声明式事务、定时任务与参数校验。
