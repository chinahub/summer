# 开发路线图

## 第一阶段：核心可用版 ✅

- [x] 工具链验证（JDK 25 + 离线 Maven）
- [x] summer-core：IoC/DI/扫描/配置
- [x] summer-web：ServerSocketChannel（阻塞，参考 Helidon NIMA）+ 虚拟线程 + TLS + chunked 请求体 + 路由 + JSON + 绑定 + 全局异常
- [x] summer-boot：启动器 + 自动配置 + 关闭钩子
- [x] summer-sample：示例 + SmokeTest（11 路由全通）

## 第二阶段：日志与运维 ✅

- [x] JUL 双通道日志（控制台 + 文件滚动）
- [x] 日志配置项（级别/目录/大小/历史）
- [x] 按天滚动自研 `DailyRollingFileHandler`（time / size-time）
- [x] 实测验证：`logs/summer.yyyy-MM-dd.log` 正确生成
- [x] 修复关闭时 `LogManager` 钩子与框架钩子竞态导致的 NPE

## 第三阶段：数据访问 ORM ✅

- [x] summer-data：实体注解 + 元数据解析 + 条件构造器 + BaseMapper + 分页 + IService
- [x] 纯 JDBC 实现（零第三方依赖，驱动由使用者自备）
- [x] summer-boot 自动配置：DataSource + Mapper 代理注册
- [x] OrmSmokeTest 28 项断言全过（SQL 生成/Wrapper/Lambda/分页/元数据）

## 第四阶段：企业级特性 ✅

- [x] YML 配置文件支持（`YamlParser`，`application.yml` 优先于 `.properties`）
- [x] 多 SQL 方言分页（MySQL/PostgreSQL/Oracle/SqlServer，`Dialect.of`）
- [x] AOP（`@Aspect` + `@Around/@Before/@After/@AfterReturning/@AfterThrowing`，JDK 动态代理 + 拦截器链）
- [x] 声明式事务 `@Transactional`（ThreadLocal 连接栈，嵌套加入，提交/回滚实测）
- [x] 无接口子类代理（手写字节码 SubclassProxyFactory，零依赖 CGLIB 替代，桥接方法破自调用递归）
- [x] 定时任务 `@Scheduled`（cron 5 段表达式 + fixedRate/fixedDelay，虚拟线程执行）
- [x] 参数校验 `@Valid`（`@NotNull/@NotBlank/@NotEmpty/@Min/@Max/@Size/@Pattern/@Email`，递归嵌套，400 违规列表）
- [x] DbSmokeTest 16 项断言全过（连接真实 PostgreSQL）

## 第五阶段：后续扩展（待做）

> 详细可行性分析与实现方案见 [高级特性研究](research-advanced.md)。

### P0 — 稳定性与安全性
- [x] ~~CGLIB 缺失时显式报错~~ → 已由自研子类代理取代（无接口 bean 自动走 SubclassProxyFactory，final/工厂方法 bean 仍显式报错）
- [x] 连接池借出超时（`pool.poll(timeout)`，避免池满永久阻塞）
- [x] 连接池泄漏检测（后台虚拟线程扫描 + WARN 日志 + 借出栈）

### P1 — 常用能力
- [ ] 异步控制器（`CompletableFuture` 返回，虚拟线程下 `join()` 方案，~5 行）
- [x] 连接池空闲保活 + 最大生存期回收
- [x] 连接池鲁棒性增强（HikariCP 风格：max-lifetime ±2.5% 抖动、minimum-idle 保活补建、借出懒创建自愈、keepalive 探活；修复 #14 抽干事故）
- [ ] 切点表达式扩展（`@annotation`、`bean()`、`within` 等）

### P2 — 协议与扩展
- [x] WebSocket（`@WebSocketEndpoint`，纯 JDK 握手+帧协议，见 [WebSocket](../使用文档/websocket.md)）
- [x] HTTP keep-alive（连接复用 + idle 超时中断 + maxRequestsPerConnection 上限，`Connection: keep-alive`）
- [x] chunked transfer-encoding（请求体 `Transfer-Encoding: chunked` 解码：chunk-size 行 + 扩展 + trailer）
- [x] 多数据源（@DS/@Master/@Slave + @DSTransactional 跨源事务）
- [ ] 静态资源（当前定位微服务框架，暂不实现）

### 不实现
- 引入 ASM/ByteBuddy 等字节码第三方库（违反零第三方依赖原则；子类代理已用纯 JDK 手写字节码实现）
## 第六阶段：安全模块 ✅

- [x] summer-security：纯 JDK 安全模块（参考 Spring Security）
- [x] JWT 无状态认证（HS256，javax.crypto.Mac，base64url）
- [x] /login 登录端点 + AuthenticationManager/UserDetailsService/DaoAuthenticationProvider
- [x] BCrypt 密码编码（纯 JDK Blowfish/EksBlowfish，$2a$ 格式，与 Spring 兼容）
- [x] URL 级授权（HttpSecurity DSL + AuthorizationFilter，permitAll/authenticated/hasRole/hasAuthority）
- [x] 方法级授权（@PreAuthorize/@PermitAll/@DenyAll，dispatcher 路由匹配后强制）
- [x] @AuthenticationPrincipal 参数注入（UserDetails 自动重载）
- [x] SecurityContextHolder（ThreadLocal，虚拟线程友好，请求结束清理）
- [x] SecurityAutoConfiguration（opt-in，默认关闭，零影响现有应用）
- [x] SecuritySmokeTest 18 项全通过（登录/URL授权/方法授权/篡改token/404回归）
- [x] ClassPathScanner 优化（O(1) jar 包探测跳过无关依赖）

## 第七阶段：工具集 utils ✅

- [x] `StringUtil`（参考 commons-lang3 StringUtils）：判空/截取/split/join/填充/替换/大小写/判断等 ~90 个方法，全 `null` 容错
- [x] `DateUtil`（参考 hutool DateUtil）：基于 `java.time` 的格式化/解析/偏移/区间/边界/字段提取，`Date`↔`LocalDateTime` 互转
- [x] `JsonUtil`（参考 hutool JSONUtil）：纯 JDK 序列化/解析/类型绑定，含 `JSONObject`/`JSONArray`，支持 record/Bean/Map/集合/枚举/`Optional`/`java.time`
- [x] `SecurityUtil`（参考 hutool SecureUtil）：MD5/SHA 摘要、HMAC、AES/DES 对称、RSA 非对称+签名验签、Base64/Hex、UUID
- [x] `SummerUtil`：IoC 容器静态门面，`getBean`/`registerBean`/`unregisterBean`（触发销毁回调）
- [x] IoC 容器扩展：`ApplicationContext` 新增 `registerBean`/`unregisterBean`，`SummerApplication.run()` 自动绑定上下文
- [x] 53 项工具集单测全通过（`StringUtilTest`/`DateUtilTest`/`JsonUtilTest`/`SecurityUtilTest`/`SummerUtilTest`）
- [x] 修正 `orm.md`「AOP 代理要求」：summer 同时支持 JDK 动态代理与子类代理（CGLIB 风格），无接口非 final 类 `@Transactional` 生效
- [x] 修复子类代理异常解包：`SubclassProxyCallback` 桥接调用抛出的 `InvocationTargetException` 未解包，导致调用方收到包装异常且 `@Transactional` 回滚规则失效；现已与 JDK 代理路径一致解包
- [x] 新增 `CglibTransactionalProxyTest`（6 项）：无接口 `@Transactional` 走子类代理、提交/回滚/`rollbackFor`/`noRollbackFor`/final 类报错

### 后续扩展
- [ ] **服务层方法级安全（暂缓）**：控制器层鉴权已覆盖大多数场景，服务层方法安全仅用于纵深防御，ROI 偏低故暂不实现。若需实现，首选拦截器路线（新增安全 `MethodInterceptor` + `ProxyAdvisor`，镜像 `TransactionInterceptor`），经 `JoinPoint.getMethod()` 直接读取目标方法 `@PreAuthorize`，无需增强 `SubclassProxyFactory` 复制字节码注解；仅当需对代理对象自身方法做反射时才需补注解复制
- [x] JWT refresh token
- [ ] 多 SecurityFilterChain（多链匹配）
- [x] CORS 过滤器：summer-web 新增 `CorsFilter`，`summer.web.cors.*` 配置（来源/方法/请求头/凭证/缓存时长），预检短路 + 跨域响应头，自动装配且先于安全过滤器执行
- [ ] CSRF 过滤器
- [ ] OAuth2 / OIDC 集成

## 第八阶段：大模型对话（AI）✅

- [x] summer-ai：纯 JDK 国内大模型对话抽象（OpenAI 兼容），零第三方依赖，不依赖 summer-boot
- [x] `ChatModel` 接口：同步 `call` 与 SSE 流式 `stream` 两种调用方式
- [x] `OpenAiCompatibleChatModel`：`HttpURLConnection` 阻塞式实现（无 selector），覆盖 DeepSeek/GLM/MiniMax
- [x] `ChatClient` fluent 门面：链式拼装 system/user/assistant 消息与 `ChatOptions`
- [x] 思维链解析：`reasoning_content`（思考模型特有）与 token 用量（含 `prompt_cache_hit_tokens`）
- [x] `Provider` 枚举：内置厂商默认 base-url 与模型名，配置覆盖即可切换新版本
- [x] summer-boot 自动配置：`AiAutoConfiguration` 按 `summer.ai.*` 装配，classpath 探测条件激活（opt-in）
- [x] 独立可用：summer-ai 仅依赖 summer-core，可脱离 summer-boot 单独使用

### 后续扩展
- [x] Embedding 向量抽象与向量库集成（EmbeddingModel/OpenAiCompatibleEmbeddingModel/VectorStore/InMemoryVectorStore/相似度检索）
- [x] Function Calling / Tool Use 调用支持（ToolCallback/Tool/ToolCallingChatModel 工具循环）
- [x] RAG 检索增强与文档分块工具（Document/TokenTextSplitter/Retriever/RetrievalAugmentationAdvisor/RagClient）
- [x] 多模态（图片/语音）消息支持（ContentPart：TextPart/ImageUrlPart/InputAudioPart）
- [x] 对话记忆与会话管理（ChatMemory/MessageWindowChatMemory/MemoryChatClient）
- [x] 重试、限流与超时熔断策略（ResilientChatModel + RetryPolicy/RateLimiter/CircuitBreaker）


## 第九阶段：文档处理（Office）✅

> 自研解析与生成 xlsx / docx / pdf / xml / csv / md 等格式文件。
> 模块 `summer-office` 仅依赖 `summer-core`（与 summer-ai 同构），以 `optional` 被 `summer-boot` 引入，
> 启动时按 classpath 探测条件激活 `OfficeAutoConfiguration`；未引入时零影响。
>
> **格式支持范围**：Excel 仅支持 XLSX（OOXML），不支持过时的 XLS（BIFF8）。读取前按魔数校验文件类型，传入 XLS 文件会抛出明确错误提示转换。不支持 XLS 的原因见下方“XLS 格式处理决策”说明。

### 已实现

- [x] 核心抽象：`OfficeReader`/`OfficeWriter`（文本类）、`TableReader`/`TableWriter`（表格类）、`TableData`、`OfficeFormat`、`OfficeException`
- [x] CSV 读写（`CsvReader`/`CsvWriter`）：纯 JDK，RFC 4180 兼容，支持引号转义、自定义分隔符、可选 BOM
- [x] Markdown 读写（`MarkdownReader`/`MarkdownWriter`）：纯 JDK，UTF-8 纯文本
- [x] XML 读写（`XmlReader`/`XmlWriter`）：纯 JDK，StAX 流式，禁用外部实体与 DTD（防 XXE）
- [x] XLSX 流式读写（`XlsxReader`/`XlsxWriter`）：纯 JDK，SAX 事件读取 + ZipOutputStream 流式写出，零第三方依赖；`RowHandler` 逐行回调 O(1) 内存
- [x] XLSX 文件类型校验（`Excel.requireXlsx`）：读取前按魔数校验，仅支持 XLSX（ZIP/OOXML），拒绝过时的 XLS（BIFF8）与其他格式，报错信息明确
- [x] `Excel` fluent API：TableData 模式 + `streamingRead`/`streamingWrite` 流式回调，纯 JDK 零依赖
- [x] DOCX 读写（`DocxReader`/`DocxWriter`）：纯 JDK（ZipFile + StAX），解析/生成 word/document.xml，遍历段落与表格提取文本
- [x] PDF 读写（`PdfReader`/`PdfWriter`）：纯 JDK，直接生成 PDF 对象结构（Catalog/Page/Font/Content Stream + xref）；读取采流扫描 + zlib 解压 + 文本操作符解析
- [x] `Office` 门面：`Office.create()` 统一入口，6 种格式工厂方法，summer-boot 自动配置注入 Bean

### 第三方库调研与选型（历史调研，已全部改为纯 JDK 实现）

> 以下为初期调研记录。经评估后决定 **全部格式纯 JDK 自研实现，零第三方依赖**：
> - xlsx/docx/pdf 均为 OOXML/PDF 文本格式，纯 JDK（java.util.zip + StAX/SAX + 手写 PDF 对象）可实现
> - 移除 FastExcel、iText、Apache POI 等全部第三方依赖，与框架“零依赖”核心原则一致
> - 许可证无须考虑（iText AGPL-3.0、Aspose 商业 EULA 均已避免）

#### XLSX -- FastExcel（采用）

| 项目 | 内容 |
| --- | --- |
| 坐标 | `cn.idev.excel:fastexcel:1.3.0` |
| 许可证 | Apache-2.0 ✅ |
| 说明 | EasyExcel 的社区 fork（CodePhiliaX/FastExcel），API 与 EasyExcel 3.x 兼容；SAX 流式读取低内存 |
| 读取 API | `FastExcel.read(in).sheet(0).doReadSync()` -> `List<Object>`（每行为 `Map<Integer,String>`）；Bean 模式 `FastExcel.read(in).head(T.class).sheet().doReadSync()` -> `List<T>` |
| 写入 API | `FastExcel.write(out).head(headers).sheet("Sheet1").doWrite(data)`；Bean 模式 `FastExcel.write(out, T.class).sheet().doWrite(list)` |
| 传递依赖 | Apache POI 5.4.1（poi + poi-ooxml）、xmlbeans、commons-compress、ehcache、commons-io、commons-csv、slf4j-api |
| 结论 | **采用**。Apache-2.0 兼容、fluent API 简洁、SAX 流式低内存；传递引入 POI 同时覆盖 docx 需求 |

#### XLSX 备选 -- Apache POI（原始方案）

| 项目 | 内容 |
| --- | --- |
| 坐标 | `org.apache.poi:poi-ooxml:5.4.1` |
| 许可证 | Apache-2.0 ✅ |
| 说明 | 原始 OOXML 处理库；API 较底层（`XSSFWorkbook`/`XSSFSheet`/`XSSFRow`/`XSSFCell`），大文件需手动 `SXSSFWorkbook` |
| 结论 | FastExcel 已传递引入 POI 5.4.1；如需更底层的 POI API 可直接使用，无需额外依赖 |

#### DOCX -- Apache POI XWPF（采用，FastExcel 传递引入）

| 项目 | 内容 |
| --- | --- |
| 坐标 | `org.apache.poi:poi-ooxml:5.4.1`（FastExcel 传递引入） |
| 许可证 | Apache-2.0 ✅ |
| 读取 API | `new XWPFDocument(in)` -> `getParagraphs()` -> `getText()`；表格 `getTables()` -> `getRows()` -> `getTableCells()` |
| 写入 API | `new XWPFDocument()` -> `createParagraph()` -> `createRun()` -> `setText()` |
| 结论 | **采用**。Apache-2.0 兼容、API 成熟；由 FastExcel 传递引入无需额外声明 |

#### DOCX 替代方案 -- Aspose.Words（不采用）

| 项目 | 内容 |
| --- | --- |
| 坐标 | `com.aspose:aspose-words:24.x`（Maven 需配置 Aspose 仓库） |
| 许可证 | 商业付费（Aspose EULA） ❌ |
| 优势 | 功能极强：DOCX/PDF/RTF/HTML 互转、邮件合并、修订追踪、表格渲染 |
| 劣势 | 收费（年费高）；不开源；传递闭包大 |
| 结论 | **不采用**。框架为 Apache-2.0 开源项目，引入商业闭源库与许可证冲突 |

#### PDF -- iText 7（采用）

| 项目 | 内容 |
| --- | --- |
| 坐标 | `com.itextpdf:kernel`+`io`+`layout`（7.2.5） |
| 许可证 | AGPL-3.0 ⚠️（或商业许可） |
| 优势 | PDF 排版能力最强：`Document`+`Paragraph` 自动分页与文本排版、复杂表格、数字签名、PDF/A |
| 读取 API | `new PdfDocument(new PdfReader(in))` -> `PdfTextExtractor.getTextFromPage(page)` 逐页提取 |
| 写入 API | `new PdfDocument(new PdfWriter(out))` + `new Document(pdfDoc)` -> `doc.add(new Paragraph(line))` 自动分页 |
| 结论 | **采用**。排版能力优于 PDFBox（手动 `PDPageContentStream` 布局），`Document` 高级 API 自动分页简洁；使用者须遵守 AGPL-3.0 或购买商业许可，Apache PDFBox 为 Apache-2.0 替代方案 |

#### PDF 备选 -- Apache PDFBox（Apache-2.0 替代方案）

| 项目 | 内容 |
| --- | --- |
| 坐标 | `org.apache.pdfbox:pdfbox:2.0.4`（或 3.0.3） |
| 许可证 | Apache-2.0 ✅ |
| 文本提取 | `PDDocument.load(in)` -> `PDFTextStripper.getText(document)` |
| PDF 生成 | `new PDDocument()` -> `addPage(PDPage)` -> `PDPageContentStream` -> `beginText/showText/endText`（手动分页） |
| 结论 | **备选**。若使用者不接受 iText AGPL，可替换为本实现（Apache-2.0）；排版需手动处理分页与字体，代码量较大 |

#### PDF 备选 -- OpenPDF

| 项目 | 内容 |
| --- | --- |
| 坐标 | `com.github.librepdf:openpdf:2.0.3` |
| 许可证 | LGPL-2.1 / MPL-2.0 ⚠️ |
| 说明 | iText 5 的社区 fork，API 与旧版 iText 5 兼容；LGPL 宽于 AGPL 但仍有 copyleft 限制 |
| 结论 | 备选。若 iText AGPL 与 PDFBox 排版均不满足，可考虑 |

#### 其他格式实现方案

| 格式 | 实现方式 | 依赖 | 状态 |
| --- | --- | --- | --- |
| XML | JDK 内置 StAX（`javax.xml.stream`） | 零第三方依赖 | ✅ 已实现 |
| CSV | 纯 JDK 手写（RFC 4180） | 零第三方依赖 | ✅ 已实现 |
| Markdown | 纯 JDK 读写（UTF-8 文本） | 零第三方依赖 | ✅ 已实现 |
| XLSX | 纯 JDK（SAX 读取 + ZipOutputStream 写出） | 零第三方依赖 | ✅ 已实现 |
| DOCX | 纯 JDK（ZipFile + StAX 解析 / ZipOutputStream 生成） | 零第三方依赖 | ✅ 已实现 |
| PDF | 纯 JDK（直接生成/解析 PDF 对象结构） | 零第三方依赖 | ✅ 已实现 |

### 许可证兼容性总结

| 库 | 许可证 | 是否采用 | 说明 |
| --- | --- | --- | --- |
| FastExcel | Apache-2.0 | ✅ 采用 | xlsx 读写引擎，传递引入 POI |
| Apache POI | Apache-2.0 | ✅ 采用 | docx 读写（XWPF），FastExcel 传递引入 |
| iText 7 | AGPL-3.0 | ✅ 采用（optional） | pdf 读写；使用者须遵守 AGPL 或替换为 PDFBox |
| Apache PDFBox | Apache-2.0 | 备选 | iText AGPL 替代方案，可按需替换 |
| OpenPDF | LGPL-2.1 / MPL-2.0 | 备选 | iText 5 fork，copyleft 限制 |
| Aspose.Words | 商业 EULA | ❌ 不采用 | 闭源收费，与开源框架冲突 |

### Excel fluent API（参考 FastExcel 使用方式）

`Excel` 门面提供表格模式（TableData）与流式回调模式，纯 JDK 零依赖：

```
// 读 -> TableData（自动校验 XLSX 格式）
TableData table = Excel.read(inputStream).sheet(0).doReadSync();
// 流式读入（逐行回调，O(1) 内存）
Excel.streamingRead(inputStream, (rowIndex, cells) -> { ... });
// 流式写出 XLSX
try (XlsxWriter writer = Excel.streamingWrite(out)) { writer.beginSheet("Sheet1").writeRow(...).endSheet(); }
// 写 <- TableData
byte[] bytes = Excel.write(table).sheet("Sheet1").doWrite();
```

同时 `ExcelReader`/`ExcelWriter` 实现 `TableReader`/`TableWriter` 接口，与 CSV 共用 `TableData` 抽象。

### 设计原则

- **全部纯 JDK**：所有格式（csv/md/xml/xlsx/docx/pdf）均纯 JDK 自研实现，零第三方依赖，与框架“零依赖”核心原则完全一致
- **无第三方库**：移除 FastExcel、iText、Apache POI 等全部依赖；xlsx/docx/pdf 均为纯 JDK 实现，无许可证顾虑
- **许可证无顾虑**：全部纯 JDK 实现，无 iText AGPL-3.0、Aspose 商业 EULA 等许可证问题
- **独立可用**：summer-office 仅依赖 summer-core，可脱离 summer-boot 单独使用（与 summer-ai 同构）
- **仅支持 XLSX**：Excel 读写仅支持 XLSX（OOXML），不支持过时的 XLS（BIFF8）；读取前按魔数校验，XLS 文件报错提示转换。原因：XLS 已弃用且有 65536 行硬限制（与大文件场景矛盾）、BIFF8 二进制纯 JDK 实现成本极高、XLS 无流式写出方案

### POI SXSSF/XSSF 源码分析与功能差距

> 基于 Apache POI 5.4.1 源码（`org.apache.poi.xssf.streaming` / `org.apache.poi.xssf.eventusermodel`），
> 对照 summer-office 当前纯 JDK 实现（`XlsxReader`/`XlsxWriter`），梳理已覆盖与待补全功能。

#### 一、SXSSF 流式写出机制（POI BigGridDemo 策略）

POI `SXSSFWorkbook` 的核心是"行窗口 + 临时文件 + 模板注入"三段式：

| 机制 | POI 实现 | summer-office 实现 | 状态 |
| --- | --- | --- | --- |
| 行窗口刷新 | `TreeMap<Integer,SXSSFRow>` 保留 N 行（默认 100），`flushOneRow` 刷新最旧行到磁盘 | 直接写 `ZipOutputStream`，无需窗口/临时文件 | ✅ 更简洁 |
| 临时文件 | `SheetDataWriter` 写 sheetData XML 片段到磁盘；`GZIPSheetDataWriter` 可压缩 | 无临时文件，行数据直写 ZIP 条目流 | ✅ 省磁盘 I/O |
| 模板注入 | 先 `XSSFWorkbook.write` 生成空壳 XLSX，再 `copyStreamAndInjectWorksheet` 替换 sheetData | 直接拼装完整 ZIP 结构（worksheet/sharedStrings/styles/rels/contentTypes） | ✅ 已实现 |
| Zip64 | `Zip64Mode` 支持单文件 >4GB / >65535 条目 | `ZipOutputStream` 默认不支持 Zip64 | ⚠️ 待补 |
| 自动列宽 | `AutoSizeColumnTracker` 刷新前统计最佳列宽 | 未实现 | ❌ 待补 |

`SheetDataWriter.writeCell` 单元格类型对照：

| 单元格类型 | XML 结构 | POI | summer-office | 状态 |
| --- | --- | --- | --- | --- |
| 空白 | `<c r="A1"/>` | ✅ | ✅ null -> 空单元格 | ✅ |
| 数值 | `<c t="n"><v>3.14</v>` | ✅ | ✅ Number -> double | ✅ |
| 布尔 | `<c t="b"><v>1</v>` | ✅ | ✅ Boolean -> 1/0 | ✅ |
| 共享字符串 | `<c t="s"><v>idx</v>` | ✅ SharedStringsTable 去重 | ✅ LinkedHashMap 去重 | ✅ |
| 内联字符串 | `<c t="inlineStr"><is><t>文本</t></is>` | ✅ 无 SST 时回退 | ❌ 仅共享字符串 | ⚠️ 待补 |
| 公式 | `<c><f>SUM(A1:A3)</f><v>6</v>` | ✅ 公式 + 缓存值 | ❌ 不支持公式写出 | ❌ 待补 |
| 错误 | `<c t="e"><v>#DIV/0!</v>` | ✅ FormulaError 枚举 | ❌ 不支持错误单元格 | ❌ 待补 |
| 日期 | 数值 + 日期 numFmt 样式 | ✅ Date -> 序列号 + 数字格式 | ❌ Date 未按序列号写 | ❌ 待补 |

行/列/样式属性对照：

| 属性 | POI | summer-office | 状态 |
| --- | --- | --- | --- |
| 单元格引用 `r`（A1） | ✅ CellReference | ✅ toCellRef/toColumnLetter | ✅ |
| 单元格样式 `s`（样式索引） | ✅ CellStyle | ❌ 仅单一 cellXfs | ❌ 待补 |
| 行自定义高度 `customHeight`/`ht` | ✅ | ❌ | ❌ 待补 |
| 行隐藏 `hidden` | ✅ | ❌ | ❌ 待补 |
| 行大纲级别 `outlineLevel` | ✅ | ❌ | ❌ 待补 |
| `xml:space="preserve"` 前后空格 | ✅ hasLeadingTrailingSpaces | ✅ 共享字符串固定保留 | ✅ |
| 列宽 `<cols>` | ✅ | ❌ | ❌ 待补 |
| 合并单元格 `<mergeCells>` | ✅ | ❌ | ❌ 待补 |
| 冻结窗格 `<pane>` | ✅ | ❌ | ❌ 待补 |
| 自动筛选 `<autoFilter>` | ✅ | ❌ | ❌ 待补 |
| 工作表维度 `<dimension>` | ✅ | ❌ | ❌ 待补 |

#### 二、XSSF 事件驱动读取机制（POI SAX）

POI `XSSFSheetXMLHandler` 基于 SAX，回调 `SheetContentsHandler` 接口：

| 回调 | POI SheetContentsHandler | summer-office RowHandler | 状态 |
| --- | --- | --- | --- |
| 行开始 | `startRow(int)` | `handle(rowIndex, cells)` 行结束回调 | ✅ 等价 |
| 行结束 | `endRow(int)` | 隐含在 handle | ✅ |
| 单元格 | `cell(cellRef, formattedValue, comment)` | 仅传 `List<String>` | ⚠️ 缺 cellRef/comment |
| 页眉页脚 | `headerFooter(text, isHeader, tagName)` | ❌ | ❌ 待补 |
| 表结束 | `endSheet()` | ❌ | ❌ 待补 |

`xssfDataType` 读取类型对照：

| 类型 | POI 处理 | summer-office 处理 | 状态 |
| --- | --- | --- | --- |
| NUMBER | 原始数值 + DataFormatter 格式化 | 返回原始字符串 | ⚠️ 无格式化 |
| SST_STRING | 共享字符串索引查表 | ✅ 索引查表 | ✅ |
| INLINE_STRING | `<is><t>` 内联字符串 | ✅ inInlineString | ✅ |
| BOOLEAN | "0"->FALSE / "1"->TRUE | 返回原始 "0"/"1" | ⚠️ 未转布尔 |
| ERROR | "ERROR:" + 值 | 返回原始值 | ⚠️ 未标记错误 |
| FORMULA | 读 `<f>` 公式或 `<v>` 缓存值 | ❌ 不处理 `<f>` 标签，仅读 `<v>` | ⚠️ 仅读缓存值 |

**样式/日期识别（关键差距）：** POI 在 `startElement` 处理 `<c>` 标签时读 `s` 属性（样式索引），
从 `StylesTable` 查询 `formatIndex`/`formatString`，对 NUMBER 调用
`DataFormatter.formatRawCellContents(d, formatIndex, formatString)`：日期格式（如 `yyyy-MM-dd`）
-> 序列号转日期；货币/百分比按格式渲染；内置格式 `BuiltinFormats.getBuiltinFormat(index)`。
summer-office 当前忽略 `s` 属性与 `xl/styles.xml`，所有数值以原始字符串返回，**日期会显示为序列号（如 45658）而非日期**。

#### XLS 格式处理决策

POI 对 XLS（BIFF8 二进制）的流式读取由 `org.apache.poi.hssf.eventusermodel`（`HSSFEventFactory` + `MissingRecordAwareHSSFListener`）提供，与 XLSX 的 SAX 模型类似。但经评估后决定 **不支持 XLS**：

- XLS 为 2003 年前的遗留格式，微软官方已弃用，Excel 2007+ 默认使用 XLSX
- XLS 有 65536 行 / 256 列硬限制，不适合“大文件”场景（这与 SXSSF 流式读写的初衷矛盾）
- BIFF8 为复杂二进制格式（OLE2 容器 + BIFF 记录），纯 JDK 实现成本极高，必须引入 POI（违反零依赖原则）
- XLS 无流式写出方案（POI HSSF 为全内存，无 SXSSF 等价物）

因此 summer-office 仅支持 XLSX，读取前按魔数校验文件类型：`50 4B`（ZIP）为合法 XLSX；`D0 CF 11 E0`（OLE2）为过时 XLS，抛出明确错误提示转换。

#### 三、需实现功能清单（按优先级）

**P0 -- 正确性（影响数据准确）**

- [ ] 读取样式与日期识别：解析 `xl/styles.xml`，按 numFmt 判断日期格式，序列号转 `LocalDate`/格式化字符串
- [ ] 读取公式单元格：解析 `<f>` 公式文本 + `<v>` 缓存值，回调中区分公式与值
- [ ] 写出日期单元格：`Date`/`LocalDate` -> Excel 序列号 + 日期数字格式样式

**P1 -- 类型完整性**

- [ ] 读取类型化值：Boolean/Error 返回类型化值（非全部 String），增加 `RowHandler` 泛型或类型回调
- [ ] 写出公式单元格：`<f>` 公式 + 可选缓存值 `<v>`
- [ ] 写出错误单元格：`<c t="e"><v>#DIV/0!</v>`

**P2 -- 富功能（对齐 POI）**

- [ ] 写出行样式属性：自定义行高 / 隐藏 / 大纲级别
- [ ] 写出列宽与合并单元格（`<cols>` / `<mergeCells>`）
- [ ] 写出内联字符串模式（小文件无 SST，减少共享表开销）
- [ ] 写出工作表维度 `<dimension>`
- [ ] Zip64 支持（单文件 >4GB / 65535 条目上限）

### 后续扩展

- [x] Excel 大文件 SXSSF 流式读写（纯 JDK `XlsxReader` SAX 读取 + `XlsxWriter` ZipOutputStream 写出，零第三方依赖）
- [x] XLSX 文件类型校验（魔数探测，仅支持 XLSX，拒绝 XLS）
- [ ] PDF 中文支持：纯 JDK PdfWriter 当前使用 Helvetica 标准字体（仅 Latin），需嵌入 TTF 子集 + CID 字体支持 CJK
- [ ] DOCX 表格/图片/样式写入
- [ ] Markdown -> HTML 转换（可引入 commonmark-java，Apache-2.0）
