# 开发路线图

## 第一阶段：核心可用版 ✅

- [x] 工具链验证（JDK 25 + 离线 Maven + JPMS）
- [x] summer-core：IoC/DI/扫描/配置
- [x] summer-web：ServerSocket + 虚拟线程 + 路由 + JSON + 绑定 + 全局异常
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
- [x] 定时任务 `@Scheduled`（cron 5 段表达式 + fixedRate/fixedDelay，虚拟线程执行）
- [x] 参数校验 `@Valid`（`@NotNull/@NotBlank/@NotEmpty/@Min/@Max/@Size/@Pattern/@Email`，递归嵌套，400 违规列表）
- [x] DbSmokeTest 16 项断言全过（连接真实 PostgreSQL）

## 第五阶段：后续扩展（待做）

> 详细可行性分析与实现方案见 [高级特性研究](research-advanced.md)。

### P0 — 稳定性与安全性
- [ ] CGLIB 缺失时显式报错（消除 `@Transactional`/AOP 静默失效隐患，~15 行）
- [ ] 连接池借出超时（`pool.poll(timeout)`，避免池满永久阻塞）
- [x] 连接池泄漏检测（后台虚拟线程扫描 + WARN 日志 + 借出栈）

### P1 — 常用能力
- [ ] 异步控制器（`CompletableFuture` 返回，虚拟线程下 `join()` 方案，~5 行）
- [x] 连接池空闲保活 + 最大生存期回收
- [ ] 切点表达式扩展（`@annotation`、`bean()`、`within` 等）

### P2 — 协议与扩展
- [ ] WebSocket（`@WebSocketEndpoint`，纯 JDK 实现握手+帧协议，~400 行）
- [ ] HTTP keep-alive（当前每连接单请求，`Connection: close`）
- [ ] chunked transfer-encoding
- [x] 多数据源（@DS/@Master/@Slave + @DSTransactional 跨源事务）
- [ ] 静态资源（当前定位微服务框架，暂不实现）

### 不实现
- CGLIB 子类代理（违反零第三方依赖原则；面向接口编程可规避，研究见 [高级特性研究](research-advanced.md#四不支持-cglib-代理对-mvc-开发的影响)）