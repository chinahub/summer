# summer-boot 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-boot/` 目录下的文件时才会加载本文件。

## 模块职责

启动器 + 自动配置 + 模块整合。`SummerApplication.run()` 是应用唯一入口。依赖所有功能模块（core/web/data/security/ai/office）。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.boot` | 启动：SummerApplication/ApplicationRunner/ApplicationArguments/@SummerBootApplication |
| `cn.jiebaba.summe.boot.web` | Web 自动配置（WebAutoConfiguration） |
| `cn.jiebaba.summe.boot.data` | 数据源 + Mapper 自动配置（DataAutoConfiguration/MapperRegistrar） |
| `cn.jiebaba.summe.boot.security` | 安全自动配置（SecurityAutoConfiguration） |
| `cn.jiebaba.summe.boot.ai` | AI 自动配置 + JDBC 向量存储 + AI 调用日志（AiAutoConfiguration/JdbcVectorStore/JdbcAiCallLogger） |
| `cn.jiebaba.summe.boot.ocr` | OCR 自动配置（OcrAutoConfiguration） |
| `cn.jiebaba.summe.boot.office` | Office 自动配置（OfficeAutoConfiguration） |

## 模块约定

- **启动入口**：`SummerApplication.run(MainClass.class, args)`——唯一启动方式
- **自动配置**：每个功能模块对应一个 `*AutoConfiguration` 类，由 `SummerApplication.AUTO_CONFIG_CLASSES`
  硬编码注册（Data/Security/Web 固定注册），可选模块（AI/Office/OCR）以 classpath 存在性探测
  （`isClassPresent`，等价 @ConditionalOnClass）条件注册；第三方模块可通过 classpath 根
  `META-INF/summer.factories` 声明 `@Configuration` 类名加入（每行一个，兼容 `key=value` 格式，缺失类跳过）
- **配置加载**：classpath 根 `application.properties` 与 `application.yml`（properties 同名键优先）；
  支持 `summer.profiles.active` 激活多 profile（叠加 `application-{profile}.properties/.yml`，
  后者覆盖前者；YAML 多文档 `---` 配合 `summer.config.activate.on-profile` 条件段）；
  环境变量按需匹配（`SERVER_PORT`→`server.port`），系统属性与命令行 `--key=value` 优先级更高
- **条件注解**：`@ConditionalOnMissingBean`（退避注册，最后评估）、`@ConditionalOnProperty`、
  `@ConditionalOnClass`（方法级或 @Configuration 类级，环境条件在普通 Bean 注册后评估）
- **启动诊断**：启动失败时输出 `APPLICATION FAILED TO START` 报告（阶段/根因/建议）并回收已创建资源
- **生命周期事件**：refresh 完成发布 `ContextRefreshedEvent`，全部 Runner 执行后发布 `ApplicationReadyEvent`，
  启动失败（容器已刷新）发布 `ApplicationFailedEvent`，关闭销毁前发布 `ContextClosedEvent`
- **关闭钩子**：注册 JVM shutdown hook，优雅关闭
