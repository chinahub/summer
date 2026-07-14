# Summer 项目架构评审报告

> 评审版本：3.0.0 | 评审日期：2026-07-13 | 核实日期：2026-07-14 | 面向：AI Agent（结构化机器可读）

## project_profile

```yaml
name: Summer
type: lightweight-java-microservice-framework
inspiration: Spring Boot
version: 3.0.0
java_version: JDK 25 (maven.compiler.release=25)
total_java_files: ~450
total_loc: ~32,000 (src ~12,000 + test ~5,500 + office ~4,100)
modules: 11
external_deps: [slf4j-api (optional), postgresql-jdbc (build-test/sample only)]
git_commits: 74
build_tool: Maven + mvnd
build_script: build.ps1 (PowerShell, Windows-only)
ci: CodeQL only (no build verify)
```

## module_inventory

```yaml
summer-core:
  files: 73
  loc: 6,297
  deps: [slf4j-api (optional)]
  packages: [annotation, aop, aop.bytecode, context, env, json, logging, logging.slf4j, scanner, scheduling, test, util]
  note: includes self-built test framework in src/main (by design)

summer-web:
  files: 75
  loc: 5,703
  deps: [summer-core]
  packages: [annotation, bind, convert, cors, filter, http, json (REMOVED), multipart, routing, server, support, validation, websocket]

summer-data:
  files: 47
  loc: 3,100
  deps: [summer-core]
  packages: [annotation, conditions, datasource, dialect, mapper, metadata, page, service, support, transaction]

summer-security:
  files: 49
  loc: 3,091
  deps: [summer-core, summer-web]
  packages: [annotation, authentication, authorization, core, crypto, jwt, userdetails, web, web.csrf]
  web_imports: 49

summer-ai:
  files: 50
  loc: 2,367
  deps: [summer-core]
  packages: [chat, chat.content, document, embedding, embedding.openai, memory, model, model.openai, rag, retry, tools, vectorstore]

summer-office:
  files: 36
  loc: 4,138
  deps: [summer-core]
  packages: [csv, docx, excel, md, ocr, pdf, xml]

summer-boot:
  files: 14
  loc: 1,188
  deps: [summer-core, summer-web, summer-data, summer-security, summer-ai (optional), summer-office (optional)]

summer-boot-loader:
  files: 1
  loc: 216
  deps: []
  role: executable-jar-launcher

summer-pack-maven-plugin:
  files: 2
  loc: 312
  deps: [maven-core (external)]
  role: repackage-mojo

summer-sample:
  files: 25
  loc: 702
  deps: [summer-boot, postgresql]

build-test:
  files: ~80
  loc: 5,540
  deps: [ALL source modules, postgresql]
  note: centralized test module; uses self-built TestRunner (cn.jiebaba.summer.core.test.*); surefire skipped
```

---

## issue_index

| issue_id | severity | category | title | fixed |
|----------|----------|----------|-------|-------|
| I-01 | P2 | ci | CI 流水线缺失（仅 CodeQL） | ❌ |
| I-02 | P1 | architecture | 两套 JSON 库重复 | ✅ |
| I-03 | P2 | architecture | SLF4J Provider 实现粗糙 | — (豁免) |
| I-04 | P1 | architecture | IoC 容器类型安全与并发问题 | ❌ |
| I-05 | P1 | code_quality | broad catch(Exception/Throwable) 过多 | ❌ |
| I-06 | P2 | architecture | summer-security 对 summer-web 耦合度高 | ❌ |
| I-07 | P1 | code_quality | Thread.sleep 误用于并发控制 | ❌ |
| I-08 | P2 | code_quality | synchronized 使用模式不一致 | ❌ |
| I-09 | P2 | code_quality | 超大文件（>400 行） | ❌ |
| I-10 | P1 | engineering | 平台硬编码（Windows-only 路径） | ❌ |
| I-11 | P2 | engineering | build-test 模块设计反模式 | ❌ |
| I-12 | P2 | engineering | Maven 依赖版本管理不完整 | ❌ |
| I-13 | P2 | engineering | Git 提交质量偏低 + 仓库垃圾文件 | ❌ |
| I-14 | P2 | regression | build.ps1 和 .gitignore 引入 UTF-8 BOM | ❌ (新增) |

---

## I-01: CI 流水线缺失

```yaml
severity: P2
file: .github/workflows/codeql.yml
description: GitHub Actions 仅配置 CodeQL 静态分析，缺少构建验证、测试运行、覆盖率检查。
missing:
  - mvn verify
  - JaCoCo coverage threshold
  - OWASP dependency scan
  - Checkstyle / SpotBugs
  - cross-platform build matrix (ubuntu, macos)
fix_status: NOT_FIXED
action: 增加至少一个包含 mvn verify 的 workflow；设置覆盖率阈值 ≥ 60%。
```

## I-02: 两套 JSON 库重复

```yaml
severity: P1 (was P1)
before:
  - { file: "summer-web/src/main/java/cn/jiebaba/summer/web/json/Json.java", loc: 993 }
  - { file: "summer-core/src/main/java/cn/jiebaba/summer/core/util/JsonUtil.java", loc: 654 }
after:
  - { file: "summer-core/src/main/java/cn/jiebaba/summer/core/json/Json.java", loc: 1035 }
  - { file: "summer-core/src/main/java/cn/jiebaba/summer/core/util/JsonUtil.java", loc: 162 }
fix_status: FIXED
detail:
  - "summer-web/json/Json.java 已删除，目录已清理"
  - "Json.java 统一迁移到 summer-core/core/json/"
  - "JsonUtil.java 从 654 行缩减到 162 行（变为轻量 facade）"
regression:
  - "合并后的 Json.java 从 993 行膨胀到 1035 行（+42 行），引入 MethodHandle 增加复杂度"
  - "JsonUtil.java 仍然存在——两套 JSON API 同时对外暴露，应评估是否彻底废弃 JsonUtil"
action: 确认 JsonUtil 是否还有调用方；若仅剩兼容性用途，标记 @Deprecated。
```

## I-04: IoC 容器类型安全与并发

```yaml
severity: P1
files:
  primary: "summer-core/.../context/DefaultApplicationContext.java (757 lines, was 732)"
  related: ["summer-core/.../aop/AdvisedProxyFactory.java", "summer-core/.../aop/SubclassProxyFactory.java"]
sub_issues:
  a:
    title: "@SuppressWarnings(\"unchecked\") 泛滥"
    before_count: 45 (42 unchecked)
    after_count: 34
    status: PARTIALLY_IMPROVED
    detail: "减少 11 处，剩余 34 处仍集中在 IoC 和 JSON 代码中"
  b:
    title: "并发工具混用（synchronizedList + ConcurrentHashMap）"
    status: NOT_FIXED
    detail: "destructionOrder 仍使用 Collections.synchronizedList，refresh() 阶段实为单线程"
  c:
    title: "循环依赖检测不完善"
    status: NOT_FIXED
    detail: "仅检测构造器循环，setter 注入循环依赖未处理"
  d:
    title: "getBeansOfType 跳过原型 Bean"
    status: NOT_FIXED
action: 逐步用具体类型替代泛型擦除路径；统一并发策略为 ConcurrentHashMap；补全循环依赖检测。
```

## I-05: broad catch(Exception/Throwable) 过多

```yaml
severity: P1
before_count: ~30 (源模块)
after_count: ~72 (源模块)
status: WORSE
reason: "summer-office 模块新增大量宽捕获（OnnxEngine.java 6处、Ocr.java 3处等）"
hotspots:
  - { file: "summer-core/.../util/SecurityUtil.java", count: 12, pattern: "catch (Exception e) { return null; }" }
  - { file: "summer-core/.../json/Json.java", count: 8, pattern: "catch (Throwable t) { throw new JsonException(...) }" }
  - { file: "summer-boot/.../SummerApplication.java", count: 5, pattern: "catch (Throwable t) { /* log and continue */ }" }
  - { file: "summer-office/.../ocr/OnnxEngine.java", count: 6, pattern: "catch (Throwable t) { throw new OcrException(...) }" }
  - { file: "summer-ai/.../OpenAiCompatibleChatModel.java", count: 4, pattern: "catch (Exception e) { throw new AiException(...) }" }
  - { file: "summer-data/.../PostgreSqlDialect.java", count: 2, pattern: "catch (Exception ignored) {}" }
pattern_analysis:
  - "大部分 broad catch 在重新抛出包装异常，但包装时丢失了 root cause 的类型信息"
  - "SecurityUtil.java 的 12 处 catch(Exception) 返回 null 是静默失败，调用方无法区分'不支持'和'出错'"
  - "Ocr.java 有 3 处 catch (Exception ignored) 完全静默吞异常"
action: |
  1. 逐文件收敛：catch(Exception) → 具体已知异常 + RuntimeException
  2. SecurityUtil 系列方法改用 Optional 返回值区分"不支持"与"出错"
  3. 禁止 catch (Exception ignored) 空块——至少 log.warning
```

## I-06: summer-security 对 summer-web 耦合度

```yaml
severity: P2
file: "summer-security/pom.xml (depends on summer-web)"
web_imports: 49 (unchanged)
status: NOT_FIXED
detail: "49 处 summer-web 引用主要集中在 web.filter、web.http、web.server 包"
mitigation: "考虑提取 summer-web-api 接口模块，security 仅依赖接口而非实现"
action: 暂不需要立即行动；若 web 模块重构频繁则优先处理。
```

## I-07: Thread.sleep 误用于并发控制

```yaml
severity: P1
count: 11 (unchanged)
status: NOT_FIXED
locations:
  - { file: "summer-web/.../server/SummerWebServer.java:169", usage: "accept 失败重试 1s", risk: "无指数退避、雪崩风险" }
  - { file: "summer-data/.../support/DataSourceFactory.java:232,252", usage: "连接池等待", risk: "阻塞平台线程、不可中断" }
  - { file: "summer-core/.../scheduling/ScheduledTaskRegistrar.java", usage: "6 处定时延迟", risk: "应用 ScheduledExecutorService.scheduleWithFixedDelay" }
  - { file: "summer-ai/.../retry/ResilientChatModel.java:79", usage: "AI 退避", risk: "InterruptedException 被吞" }
  - { file: "summer-ai/.../retry/RateLimiter.java:38", usage: "限流等待", risk: "sleep 精度依赖 OS 调度器" }
  - { file: "summer-sample/.../HelloController.java:28", usage: "模拟延迟", risk: "示例代码不宜含 sleep" }
action: |
  1. SummerWebServer: 用 ScheduledExecutorService.schedule + 指数退避
  2. DataSourceFactory: 用 CountDownLatch 或 LockSupport.parkNanos
  3. ScheduledTaskRegistrar: 本身就有 ScheduledExecutor，消除 Thread.sleep 改用 API
  4. ResilientChatModel: 用 LockSupport.parkNanos 替代 sleep，正确处理中断
```

## I-08: synchronized 使用模式不一致

```yaml
severity: P2
status: NOT_FIXED
affected_classes:
  - { class: "CircuitBreaker", strategy: "synchronized method", issue: "每次获取和释放都独占" }
  - { class: "RateLimiter", strategy: "synchronized block", issue: "与 CircuitBreaker 风格不一致" }
  - { class: "InMemoryVectorStore", strategy: "synchronized method (读+写)", issue: "读多写少场景浪费——应用 ReadWriteLock" }
  - { class: "DailyRollingFileHandler", strategy: "synchronized method", issue: "日志写入热点路径，独占锁影响吞吐" }
action: |
  1. InMemoryVectorStore: 改用 ReentrantReadWriteLock
  2. CircuitBreaker/RateLimiter: 统一使用 ReentrantLock + Condition
  3. DailyRollingFileHandler: 评估是否真正需要线程安全（JUL handler 通常单线程调用）
```

## I-09: 超大文件（>400 行）

```yaml
severity: P2
before_largest: "Json.java (993 lines in summer-web)"
after_largest: "Json.java (1035 lines in summer-core)"
status: NOT_FIXED
files_gt_400:
  - { file: "summer-core/.../json/Json.java", loc: 1035, note: "比之前还大 +42 行" }
  - { file: "summer-core/.../context/DefaultApplicationContext.java", loc: 757, note: "+25 行" }
  - { file: "summer-data/.../support/DataSourceFactory.java", loc: 609 }
  - { file: "summer-core/.../util/StringUtil.java", loc: 606 }
  - { file: "summer-security/.../crypto/BCrypt.java", loc: 489 }
  - { file: "summer-web/.../server/SummerWebServer.java", loc: 458 }
  - { file: "summer-office/.../ocr/OnnxEngine.java", loc: 442, note: "新增" }
  - { file: "summer-web/.../http/RawHttpRequest.java", loc: 419 }
ag_ents_md_violation: "超 20 行方法须有中文注释——这些大文件普遍方法过长"
action: |
  1. Json.java: 拆分 parse 和 serialize 为独立类
  2. DefaultApplicationContext: 将 Bean 生命周期（instantiate/populate/initialize/destroy）拆为策略类
  3. StringUtil: 按功能域分组（case/codec/random）为多个小工具类
```

## I-10: 平台硬编码

```yaml
severity: P1
status: NOT_FIXED
hardcoded_paths:
  - { file: "build.ps1:7", value: "$env:JAVA_HOME = 'D:\\jdk\\jdk-25.0.2'", issue: "仅限一台 Windows 机器" }
  - { file: "build.ps1:8", value: "D:\\mvnd-1.0.5\\mvn\\bin", issue: "mvnd 路径硬编码" }
  - { file: "pom.xml:172", value: "<executable>D:\\Git\\usr\\bin\\gpg.exe</executable>", issue: "GPG 签名仅 Windows" }
  - { file: "pom.xml:16", value: "<maven.compiler.release>25</maven.compiler.release>", issue: "JDK 25 早期阶段" }
missing:
  - "无 build.sh / Makefile / mvnw 等跨平台构建入口"
  - "settings.xml 无 <servers> 凭据（依赖全局 ~/.m2/settings.xml）"
action: |
  1. JAVA_HOME 改为读取环境变量，fallback 到 java.home 系统属性
  2. GPG executable 改为 gpg（依赖 PATH），或仅在 release profile 中激活
  3. 添加 build.sh（bash）作为跨平台入口
  4. 增加 mvnw（Maven Wrapper）确保构建工具版本一致性
```

## I-11: build-test 模块设计反模式

```yaml
severity: P2
status: NOT_FIXED
problem: "build-test 依赖所有源模块，无法按模块独立测试"
deps: [summer-core, summer-data, summer-boot, summer-ai, summer-office, summer-sample, postgresql]
fix_status: BY_DESIGN (当前策略：集中测试模块)
risk: "任一源模块变更 → 全量编译；测试隔离性差"
action: 长期建议将测试按模块拆分到各 src/test，build-test 保留跨模块集成测试。
```

## I-12: Maven 依赖版本管理不完整

```yaml
severity: P2
status: NOT_FIXED
managed_in_dependency_management: [slf4j-api]
unmanaged_hardcoded:
  - { artifact: "org.postgresql:postgresql", version: "42.7.11", locations: ["build-test/pom.xml:58", "summer-sample/pom.xml:24"] }
  - { artifact: "org.codehaus.mojo:exec-maven-plugin", version: "3.5.0", location: "build-test/pom.xml:79" }
action: 将所有外部依赖版本提升到 parent pom.xml 的 <dependencyManagement>。
```

## I-13: Git 提交质量 + 仓库垃圾

```yaml
severity: P2
status: NOT_FIXED
commit_issues:
  - "中英混杂消息（summer office支持、提交代码规范）"
  - "无 conventional commits 格式"
  - "部分提交消息过长含 checklist"
repo_clutter:
  - { path: "code_scan/", size: "14 MB (index.html)", status: "已在仓库中，应移除或 gitignore" }
  - { path: "verify-tmp/", size: "含编译 .class 文件", status: "未 gitignore，应清理" }
  - { path: "logs/", status: "已在 .gitignore ✅" }
action: |
  1. 采用 conventional commits: feat:/fix:/chore:/docs:/refactor:
  2. 将 code_scan/ 和 verify-tmp/ 加入 .gitignore
  3. 若 code_scan 确需保留，移到独立仓库或 GitHub Pages
```

## I-14 (NEW): UTF-8 BOM 污染回归

```yaml
severity: P2
status: NEW_REGRESSION
ag_ents_md_rule: "UTF-8 无 BOM（前导字节 EF BB BF）"
violations:
  - { file: "build.ps1", bom: "EF BB BF", note: "上次分析时无 BOM" }
  - { file: ".gitignore", bom: "EF BB BF" }
impact:
  - "shebang 失效（# 不是文件首字节）"
  - "可能导致 Windows 工具链解析异常"
  - "diff 产生不可见字节差异"
action: 用 Python 二进制模式读取、去掉 b'\xef\xbb\xbf' 后写回。
```

---

## scorecard

```yaml
modularity: { score: 4, max: 5, comment: "模块划分清晰，依赖方向正确" }
zero_dependency: { score: 5, max: 5, comment: "除 SLF4J API 外零外部依赖，执行彻底" }
test_coverage: { score: 2, max: 5, comment: "自研测试框架 + 集中式测试模块（by design）" }
code_quality: { score: 3, max: 5, comment: "broad catch 未收敛、Thread.sleep 未替换、大文件未拆分" }
ci_cd: { score: 2, max: 5, comment: "仅 CodeQL，无构建验证" }
documentation: { score: 3, max: 5, comment: "中文使用文档齐全，无 API JavaDoc" }
engineering: { score: 2, max: 5, comment: "平台硬编码、BOM 回归、依赖版本散落" }
portability: { score: 1, max: 5, comment: "Windows 独占（PowerShell 构建、路径硬编码）" }
```

---

## action_plan (按优先级排序)

```yaml
P0_blocking:
  - id: I-10
    title: "消除平台硬编码"
    why: "当前只有 Windows 一台机器能构建；CI 无法运行"
  - id: I-05
    title: "收敛 broad catch(Exception)"
    why: "72 处宽捕获掩盖真实错误；SecurityUtil 12 处返回 null 是定时炸弹"
  - id: I-07
    title: "替换 Thread.sleep"
    why: "Web 服务器和连接池的 sleep 在异常路径下不可靠"

P1_important:
  - id: I-01
    title: "建立 CI 流水线"
    why: "无自动化验证，每次合并都是赌博"
  - id: I-04
    title: "IoC 容器类型安全"
    why: "34 处 @SuppressWarnings('unchecked') 仍需处理"
  - id: I-09
    title: "拆分超大文件"
    why: "Json.java 1035 行、DefaultApplicationContext 757 行违反 AGENTS.md 规范"

P2_nice_to_have:
  - id: I-12
    title: "统一 Maven 依赖版本管理"
  - id: I-13
    title: "规范 Git 提交 + 清理仓库大文件"
  - id: I-14
    title: "修复 BOM 污染"
  - id: I-08
    title: "统一 synchronized 策略"
  - id: I-06
    title: "降低 security→web 耦合（长期）"
  - id: I-11
    title: "拆分 build-test（长期）"
```

---

## dependency_graph

```
summer-core (foundation: IoC, AOP, logging, JSON, utils)
    ├── summer-web (HTTP server, routing)        ← also by summer-security (49 refs)
    ├── summer-data (ORM, datasource, tx)
    ├── summer-ai (chat, embedding, RAG, vector)
    └── summer-office (doc processing, OCR)

summer-boot (autoconfig, launcher)
    ├── summer-core
    ├── summer-web
    ├── summer-data
    ├── summer-security
    ├── summer-ai (optional)
    └── summer-office (optional)

summer-boot-loader     (JAR launcher, 1 file)
summer-pack-maven-plugin (repackage mojo)
summer-sample          (demo app → summer-boot + pg)
build-test             (centralized tests → ALL modules + pg)
```

---

## highlights

```yaml
- "零第三方依赖哲学执行彻底——核心框架仅 optional slf4j-api"
- "JDK 25 + 虚拟线程处理 HTTP 请求，架构紧跟前沿"
- "功能覆盖面广：IoC/AOP/Web/Data/Security/AI/Office"
- "编码规范基础设施齐全：.editorconfig, .gitattributes, AGENTS.md"
- "发布流程完整：Maven Central + GPG 签名 + source/javadoc"
- "JSON 库合并已完成——消除 summer-web 对 JSON 的反向依赖"
```

---

## change_log

```yaml
- date: "2026-07-13"
  version: "1.0"
  description: "初始评审——静态分析 + 架构模式识别"

- date: "2026-07-14"
  version: "1.1"
  changes:
    - "移除 I-01 (零单元测试) 和 I-11 (测试框架在 src/main) —— 自研性质，by design"
    - "更新 I-02: JSON 合并已修复，标记回归（Json.java 膨胀到 1035 行）"
    - "更新 I-04: @SuppressWarnings 从 45 → 34，部分改善"
    - "更新 I-05: broad catch 从 ~30 → ~72 处（office 模块新增），状态退化为 WORSE"
    - "新增 I-14: build.ps1 和 .gitignore 引入 UTF-8 BOM 污染"
    - "所有 issue 增加 fix_status 字段，面向 AI Agent 的结构化格式"
```
