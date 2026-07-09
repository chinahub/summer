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
- [ ] 服务层方法级安全（需增强 SubclassProxyFactory 复制方法注解）
- [ ] JWT refresh token
- [ ] 多 SecurityFilterChain（多链匹配）
- [ ] CSRF / CORS 过滤器
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
- [ ] Embedding 向量抽象与向量库集成
- [ ] Function Calling / Tool Use 调用支持
- [ ] RAG 检索增强与文档分块工具
- [ ] 多模态（图片/语音）消息支持
- [ ] 对话记忆与会话管理（Memory 抽象）
- [ ] 重试、限流与超时熔断策略
