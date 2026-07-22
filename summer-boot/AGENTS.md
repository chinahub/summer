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
- **自动配置**：每个功能模块对应一个 `*AutoConfiguration` 类，在 `summer.factories` 中注册
- **YAML 配置**：默认读取 classpath 下的 `application.yml` 或 `application.properties`
- **关闭钩子**：注册 JVM shutdown hook，优雅关闭
