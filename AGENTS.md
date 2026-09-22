# AGENTS.md

> **角色定位**：项目"宪法"——每次会话都必须加载的基础事实。控制在 200 行以内。流程类内容、工作流、安全检查清单等应放在 `.claude/skills/`，路径限定规则放在 `.claude/rules/`。
> 参考：[Anthropic 官方：七种自定义方式决策框架](https://www.toutiao.com/article/7653321298067718671)

## 项目概览

summer 是基于 JDK 25 内置库、虚拟线程构建的类 Spring Boot 轻量级 Java 微服务框架。
- **定位**：零 Servlet、尽量零第三方依赖，`java -jar` 直接运行
- **包名**：`cn.jiebaba.summer`
- **版本**：4.0
- **许可**：Apache 2.0
- **JDK**：25（`maven.compiler.release=25`）
- **构建**：Maven，多模块（parent POM，8 个子模块；web/data/security/office 已并入 summer-boot，`cn.jiebaba.summer.core.*` 独立为 summer-core）

## 构建命令

> **本机构建环境**：PATH 默认 `java` 是 JDK 17，直接 `mvn` 会报"不支持发行版本 25"。构建前须切换到 JDK 25：
> `export JAVA_HOME="D:\\jdk\\jdk-25.0.4" && export PATH="/d/jdk/jdk-25.0.4/bin:$PATH"`

```bash
# 完整构建（编译+测试）
mvn clean package

# 跳过测试
mvn clean package -DskipTests

# 构建 single module
mvn clean package -pl summer-boot -am

# 发布到 Maven Central（需 GPG 签名）
mvn clean deploy -P release
```

## 模块速览

| 模块 | 职责 | 关键内容 |
| --- | --- | --- |
| summer-core | 核心运行时（`cn.jiebaba.summer.core.*` 独立成模块，boot 与 support 的共同底层） | ApplicationContext/IoC 容器, 类扫描, AOP(自研字节码代理), JUL 日志 + SLF4J 绑定, 定时任务, 环境配置(env), 自研 JSON(json + util.JsonUtil), 框架注解, core.test 测试微框架, ONNX 引擎(onnx), SummerException 基础异常 |
| summer-boot | 启动器 + 自动配置（依赖 summer-core；整合原 web/data/security/office） | SummerApplication.run(), 各模块 AutoConfiguration, Mapper 注册, 嵌入式 HTTP(NIMA), 路由/校验/WebSocket, BaseMapper/Wrapper/分页, 多方言, @DS/@DSTransactional, JWT 无状态认证 + BCrypt + 授权, xlsx/docx/pdf/csv/md/xml 文档读写 |
| summer-support | OCR 文字识别（原 summer-office 的 OCR 独立而成，依赖 summer-core） | cn.jiebaba.summer.support.ocr: Ocr/OcrConfig/OcrResult + DB/CRNN 流水线, OcrAutoConfiguration |
| summer-ai | 大模型对话抽象 + RAG + 向量存储 + A2A 跨实例协作 | ChatClient, OpenAI 兼容, SSE 流式, 工具调用, embedding, 重试/熔断, agent(A2A 委派/契约/恢复), AI 自动配置（cn.jiebaba.summer.boot.ai 包随本模块发布） |
| summer-boot-loader | 可执行 jar 启动器 | JarLauncher（`java -jar` 入口） |
| summer-pack-maven-plugin | 打包插件 | 产出 BOOT-INF 可执行 jar |
| summer-sample | 示例应用 | controller/service/repository/aspect 示例 |
| build-test | 集中式测试 | AOP 单测/集成测试 + sample 冒烟测试 |

> 模块依赖方向：summer-support → summer-core ← summer-boot；summer-ai → summer-boot；summer-sample/build-test 依赖上述模块。
> summer-boot 不编译期依赖 summer-ai/summer-support（避免循环依赖），二者由 SummerApplication
> 按 classpath 探测（isClassPresent + Class.forName 反射）注册自动配置。

## 代码规范

### 文件编码（强制）

创建或修改任何文本文件时必须遵守：
- **编码：UTF-8，无 BOM**。禁止写入 UTF-8 with BOM
- **行尾：LF（`\n`）**。禁止 CRLF（`.bat` 等必须 CRLF 的除外）
- **文件结尾：恰好一个换行符**
- **行内空白：禁止行尾多余空格**；缩进遵循各文件既有风格（Java 用 4 空格，Tab/空格不混用）
- **shebang**：`#!/bin/sh` 等必须是文件第一个字节，前面不得有 BOM

### 方法注释（强制）

- **超 20 行方法必须有注释**：方法上方添加注释，说明用途、关键参数或返回值
- **注释一律使用中文**：专有名词、API 名称、标识符可保留原文
- **注释风格**：优先使用 JavaDoc（`/** ... */`）

### 校验工具链

- `.gitattributes`：`* text=auto eol=lf`
- `.editorconfig`：`charset=utf-8`, `end_of_line=lf`, `insert_final_newline=true`, `trim_trailing_whitespace=true`
- 开发者本机：`git config core.autocrlf input`；IDE 关闭 BOM

## 文档索引

- [docs/README.md](docs/README.md) —— 完整文档导航
- [.claude/skills/](.claude/skills/) —— 流程类工作流（代码审查、发布等）
- [.claude/rules/](.claude/rules/) —— 路径限定规则
