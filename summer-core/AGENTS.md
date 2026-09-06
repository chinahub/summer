# summer-core 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-core/` 目录下的文件时才会加载本文件。

## 模块职责

IoC/DI 核心 + 日志 + AOP + 定时任务 + 工具集。所有其他模块的基础依赖。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.core.context` | ApplicationContext 容器实现 |
| `cn.jiebaba.summe.core.scanner` | 类路径扫描与注解工具 |
| `cn.jiebaba.summe.core.aop` | AOP 框架：切面注册、代理工厂、字节码生成 |
| `cn.jiebaba.summe.core.aop.bytecode` | 自研字节码引擎（ConstantPool/ClassBuilder/MethodBuilder/Descriptor）——无 CGLIB 依赖 |
| `cn.jiebaba.summe.core.logging` | JUL 日志（DailyRollingFileHandler/SingleLineFormatter）+ SLF4J 绑定 |
| `cn.jiebaba.summe.core.scheduling` | 定时任务：CronExpression + @Scheduled 注册 |
| `cn.jiebaba.summe.core.env` | 环境/配置（YAML 解析） |
| `cn.jiebaba.summe.core.json` | 自研 JSON 解析（JsonReader/Json/TypeReference） |
| `cn.jiebaba.summe.core.util` | 工具集：StringUtil/DateUtil/JsonUtil/SecurityUtil/ReflectionUtils/SummerUtil |
| `cn.jiebaba.summe.core.annotation` | 框架注解：@Order/@Controller 等 |
| `cn.jiebaba.summe.core.test` | 测试微框架：@SummerTest/SummerExtension |

## 模块约定

- **零第三方依赖**：本模块禁止引入任何第三方库（SLF4J API 除外）
- **AOP 代理**：使用自研字节码引擎（`summer-core/aop/bytecode/`），不依赖 CGLIB 或 ByteBuddy。接口用 JDK 动态代理，无接口类用于类代理
- **日志**：底层使用 JUL（`java.util.logging`），提供自研 SLF4J→JUL 绑定和 MDC 适配器
- **配置**：支持 YAML（`application.yml`，含 `---` 多文档与 `summer.config.activate.on-profile` 条件段）和 Properties 两种格式；`summer.profiles.active` 激活多 profile 叠加 `application-{profile}.*`；环境变量按需匹配（`SERVER_PORT`→`server.port`），系统属性与命令行 `--key=value` 优先级更高
- **事件**：`@EventListener` 方法监听，`ApplicationContext.publishEvent` 同步发布（类型可赋值匹配，监听器惰性创建）；内置 `ContextRefreshedEvent`/`ContextClosedEvent`
- **声明式绑定**：`@ConfigurationProperties` 按前缀绑定字段（kebab-case 键；支持简单类型/List/Map/嵌套 POJO），绑定在属性注入后执行
