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
- [x] pgvector 持久化向量库（summer-boot `JdbcVectorStore` + `VectorTypeHandler`，复用 summer-data `SqlExecutor`/`RowMapper`，HNSW 余弦索引）
- [x] 流式工具调用循环（`ToolCallingChatModel.stream` 按 `index` 跨 SSE 帧累积 `tool_calls` 增量、执行工具、续接流式；同步与流式语义一致）
- [x] 工具调用自动装配（`summer.ai.tools.enabled=true` 收集 `ToolCallback` bean，自动将 `ChatModel` 包装为 `ToolCallingChatModel`）
- [x] 文档读取器（`TextReader` + summer-boot `OfficeDocumentReader`，XLSX/DOCX/PDF 接入 RAG）
- [x] summer-data 向量用法扩展（`RowMapper` + `SqlExecutor.query(Sql, RowMapper)` 重载，供含计算列的自定义查询与向量绑定复用）
- [x] 结构化输出 / JSON 模式（`ChatOptions` 增 `responseFormat`，请求体带 `response_format={"type":"json_object"}`）
- [x] 流式 token 用量（`stream_options.include_usage=true`，末帧 `usage` 解析填入 `ChatResponse.metadata`，工具循环 `emit` 透传）
- [x] 向量库元数据过滤（`SearchRequest` 增 `filter` 键值对等值匹配；pgvector 走 `metadata::jsonb @> ?::jsonb`，内存走谓词）
- [x] AI 调用日志 / 观测性（`LoggingChatModel` 装饰器记录每次 LLM 调用的模型/token/耗时/成败/提问摘要；`summer.ai.logging.enabled` 开关 + `ai_call_log` 表，`JdbcAiCallLogger` 复用 `SqlExecutor` 惰性建表）

### 待开发（summer-ai）
- [ ] DB 驱动厂商凭据（生产级）：`AiAutoConfiguration` 支持从 `ai_provider_info` 表实时读取 provider/baseurl/api-key（当前仅冒烟测试实现，未接入自动配置）
- [ ] 示例 AI 端点：summer-sample 增加 `/ai/**` 控制器（对话/流式/RAG/工具调用示例），目前 `summer.ai.*` 段为注释状态


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
- [x] XLSX 单元格类型与结构特性（借鉴 POI StylesTable/DateUtil/XSSFSheetXMLHandler/SXSSF，纯 JDK 重写）：读取侧解析 `xl/styles.xml` 按内置 id 与 numFmt 识别日期、序列号转日期串，Boolean 转 TRUE/FALSE、Error 返回错误码、公式返回缓存值（无缓存则返回公式文本）；写出侧支持 `LocalDate`/`LocalDateTime`/`LocalTime`/`Date`/`Instant` 日期、`Formula`（公式+可选缓存）、`ErrorValue`（错误码）、合并单元格、列宽、冻结窗格、自动筛选、工作表维度、行高；序列号转换与日期识别已对照 POI 5.4.1 `DateUtil`/`isInternalDateFormat` 验证一致
- [x] DOCX 读写（`DocxReader`/`DocxWriter`）：纯 JDK（ZipFile + StAX），解析/生成 word/document.xml，遍历段落与表格提取文本
- [x] PDF 读写（`PdfReader`/`PdfWriter`）：纯 JDK，直接生成 PDF 对象结构（Catalog/Page/Font/Content Stream + xref）；读取采流扫描 + zlib 解压 + 文本操作符解析
- [x] `Office` 门面：`Office.create()` 统一入口，6 种格式工厂方法，summer-boot 自动配置注入 Bean

### Excel fluent API

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
- **无第三方库**：xlsx/docx/pdf 均为纯 JDK 实现，无许可证顾虑
- **许可证无顾虑**：全部纯 JDK 实现，无 AGPL/商业 EULA 等许可证问题
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
| Zip64 | `Zip64Mode` 支持单文件 >4GB / >65535 条目 | JDK `ZipOutputStream` 条目超 65535 / 偏移超 4GB 时自动写 Zip64（实测 65537 条目生成 EOCD64 定位器） | ✅ |
| 自动列宽 | `AutoSizeColumnTracker` 刷新前统计最佳列宽 | 未实现 | ❌ 待补 |

`SheetDataWriter.writeCell` 单元格类型对照：

| 单元格类型 | XML 结构 | POI | summer-office | 状态 |
| --- | --- | --- | --- | --- |
| 空白 | `<c r="A1"/>` | ✅ | ✅ null -> 空单元格 | ✅ |
| 数值 | `<c t="n"><v>3.14</v>` | ✅ | ✅ Number -> double | ✅ |
| 布尔 | `<c t="b"><v>1</v>` | ✅ | ✅ Boolean -> 1/0 | ✅ |
| 共享字符串 | `<c t="s"><v>idx</v>` | ✅ SharedStringsTable 去重 | ✅ LinkedHashMap 去重 | ✅ |
| 内联字符串 | `<c t="inlineStr"><is><t>文本</t></is>` | ✅ 无 SST 时回退 | ✅ `inlineStrings(true)` 开启内联模式（fastexcel 写入侧仅共享串） | ✅ |
| 公式 | `<c><f>SUM(A1:A3)</f><v>6</v>` | ✅ 公式 + 缓存值 | ✅ `Formula` 类型写出 `<f>`+缓存值 | ✅ |
| 错误 | `<c t="e"><v>#DIV/0!</v>` | ✅ FormulaError 枚举 | ✅ `ErrorValue` 类型 | ✅ |
| 日期 | 数值 + 日期 numFmt 样式 | ✅ Date -> 序列号 + 数字格式 | ✅ LocalDate/DateTime/Time -> 序列号 + numFmt | ✅ |

行/列/样式属性对照：

| 属性 | POI | summer-office | 状态 |
| --- | --- | --- | --- |
| 单元格引用 `r`（A1） | ✅ CellReference | ✅ toCellRef/toColumnLetter | ✅ |
| 单元格样式 `s`（样式索引） | ✅ CellStyle | ✅ `XlsxStyles` 多样式（日期/布尔/错误等） | ✅ |
| 行自定义高度 `customHeight`/`ht` | ✅ | ✅ `rowHeight()` | ✅ |
| 行隐藏 `hidden` | ✅ | ✅ `hideRow()` | ✅ |
| 行大纲级别 `outlineLevel` | ✅ | ✅ `outlineLevel()` | ✅ |
| `xml:space="preserve"` 前后空格 | ✅ hasLeadingTrailingSpaces | ✅ 共享字符串固定保留 | ✅ |
| 列宽 `<cols>` | ✅ | ✅ `setColumnWidth()` | ✅ |
| 合并单元格 `<mergeCells>` | ✅ | ✅ `mergeCells()` | ✅ |
| 冻结窗格 `<pane>` | ✅ | ✅ `freezePanes()` | ✅ |
| 自动筛选 `<autoFilter>` | ✅ | ✅ `setAutoFilter()` | ✅ |
| 工作表维度 `<dimension>` | ✅ | ✅ 流式占位 `A1` | ✅ |

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

- [x] 读取样式与日期识别：解析 `xl/styles.xml`，按 numFmt 判断日期格式，序列号转 `LocalDate`/格式化字符串（内置 id 对齐 POI `isInternalDateFormat`，序列号转换已对照 POI `DateUtil` 验证一致）
- [x] 读取公式单元格：解析 `<f>` 公式文本 + `<v>` 缓存值，返回缓存值（日期样式则格式化），无缓存值时返回公式文本（保持 `List<String>` 契约）
- [x] 写出日期单元格：`LocalDate`/`LocalDateTime`/`LocalTime`/`Date`/`Instant` -> Excel 序列号 + 日期数字格式样式

**P1 -- 类型完整性**

- [x] 读取类型化值：Boolean 转 `TRUE`/`FALSE`、Error 返回错误码（保持 `List<String>` 契约，未引入泛型回调）
- [x] 写出公式单元格：`<f>` 公式 + 可选缓存值 `<v>`（`Formula` 类型）
- [x] 写出错误单元格：`<c t="e"><v>#DIV/0!</v>`（`ErrorValue` 类型）

**P2 -- 富功能（对齐 POI）**

- [x] 写出行高 `customHeight`、行隐藏 `hidden`、大纲级别 `outlineLevel`（`rowHeight()`/`hideRow()`/`outlineLevel()`，fastexcel 写入侧需 RowWriteHandler+POI，本类直接暴露）
- [x] 写出列宽与合并单元格（`<cols>` / `<mergeCells>`）
- [x] 写出内联字符串模式 `t="inlineStr"`（`inlineStrings(true)`，小文件无 SST，减少共享表开销；fastexcel 写入侧仅共享串，本类额外支持内联）
- [x] 写出工作表维度 `<dimension>`（流式占位 `A1`，Excel 打开自动重算）
- [x] Zip64 支持（JDK `ZipOutputStream` 条目超 65535 / 偏移超 4GB 时自动写 Zip64，实测 65537 条目生成 EOCD64 定位器）

### 后续扩展

- [x] Excel 大文件 SXSSF 流式读写（纯 JDK `XlsxReader` SAX 读取 + `XlsxWriter` ZipOutputStream 写出，零第三方依赖）
- [x] XLSX 文件类型校验（魔数探测，仅支持 XLSX，拒绝 XLS）
- [x] PDF 中文支持：嵌入 TTF（含 TTC 集合字体）+ Type0/CIDFontType2/FontDescriptor/FontFile2/ToUnicode 全结构，字形 id 十六进制编码；全字体嵌入（子集化留作后续体积优化）
- [x] DOCX 表格/图片/样式写入（`DocxWriter` 构建器：段落/标题/表格（边框+表头）/图片（EMU），`Run` 加粗/斜体/下划线/字号/颜色，styles.xml Heading1-6）
- [x] Markdown -> HTML 转换（纯 JDK `MdHtml`，CommonMark 子集 + GFM 删除线，零第三方依赖）
