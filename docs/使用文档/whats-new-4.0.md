# 4.0 增强速览（SDLC 编排类应用支撑）

> 2026-09 成批落地的框架增强。背景：以「数字员工 SDLC 流水线编排器」（长时运行工作流、
> 多实例并行、审批追踪、实时看板、用量计量）为参照做架构评审，补齐支撑此类应用的框架原语。
> 每项均附用法；细节见各专题文档。

## 1. JSON：长整型前端安全序列化 + 精确绑定（core）

**修复**：`bind`/`toBean` 把字符串形式数字绑定到 Long 字段时经 `Double.valueOf` 中转，
19 位雪花 id 丢精度；`JsonUtil.getLong/getInt/getDouble` 对字符串形式数字不兼容。

**新增** `Json.LongAsString` 序列化策略（`JsonUtil.setLongAsString` 透传）：

| 模式 | 行为 |
|---|---|
| `AUTO`（默认） | 超出 JS 安全整数（±2^53-1）的 Long/BigInteger 序列化为字符串，其余保持 JSON 数字 |
| `ALWAYS` | 所有 Long/BigInteger 一律字符串（全字符串 id 约定） |
| `NEVER` | 一律 JSON 数字（历史行为） |

```java
// 默认 AUTO：浏览器端 JSON.parse 不再静默截断雪花 id
JsonUtil.toJsonStr(Map.of("id", 4555555555555555555L)); // {"id":"4555555555555555555"}
// 解析端对称：字符串数字精确还原（bean 字段与 getLong 均兼容）
JsonUtil.toBean("{\"id\":\"4555555555555555555\"}", Holder.class);
```

## 2. SSE 广播中枢 SseHub（boot/web）

一对多推送原语：自动事件 id + 环形补发缓冲（`Last-Event-ID` 断线补发）+ 心跳注释帧保活
+ 死连接自动清理。作为 Bean 自动装配（`@ConditionalOnMissingBean`，可自定义替换）。

```java
@RestController
class NotifyController {
    private final SseHub hub; // 注入

    @GetMapping("/api/events")
    SseEmitter subscribe(@RequestHeader(value = "Last-Event-ID", required = false) String lastId) {
        return hub.subscribe(new SseEmitter(0), lastId); // 直接返回 emitter
    }

    void onStateChanged(String payload) {
        hub.broadcast(payload, "pipeline"); // 全部订阅者收到，自动分配递增 id
    }
}
```

- handler 直接返回 `Stream<SseEvent>` 也可开启事件流（拉式逐帧写出，不缓冲整个流）。
- `SseComment` 注释帧（`: text`）可用于手工心跳；`SseHub` 默认 15 秒自动发送。
- 构造参数：`new SseHub(replayCapacity, heartbeatMillis)`（补发容量 0 关闭补发、心跳 0 关闭心跳）。

## 3. 异步事件（core/context）

`@EventListener(async = true)`：监听器在独立虚拟线程执行、异常隔离（只记日志不向发布方传播）。
默认仍为同步（异常向发布方传播）。适合扇出派发等"发后即忘"场景：

```java
@EventListener(async = true)
public void onStageApproved(StageApprovedEvent e) {
    taskBoard.fanOut(e.taskBreakdown()); // 失败不影响发布方，兄弟监听器照常执行
}
```

## 4. 数据层（boot/data）

### 4.1 @Version 乐观锁

```java
class DevTask {
    @TableId private Long id;
    @Version private Integer version; // int/Integer/long/Long
}
mapper.updateById(task);
// 生成：UPDATE dev_task SET ..., version = version + 1 WHERE id = ? AND version = ?
// 受影响行数 0 → OptimisticLockException（版本冲突或记录不存在）；成功后实体版本号自增
// insert 时版本为空自动初始化为 1
```

适合多实例并行推进子任务状态的 CAS 更新。

### 4.2 insertBatch 批量插入

`mapper.insertBatch(List<T>)`：单语句多行 VALUES；AUTO 自增主键自动降级逐条 insert
（单语句多行无法跨方言回填自增 id）。列集合取"任一实体非空"字段并集，行内空值显式写 NULL。

### 4.3 upsert（六方言）

`mapper.upsert(T)`：按主键冲突判定"存在则更新、不存在则插入"；需要显式主键
（AUTO 不支持）；空值字段既不插入也不更新。

| 方言 | 语法 |
|---|---|
| MySQL/MariaDB | `ON DUPLICATE KEY UPDATE c = VALUES(c)` |
| H2 | `MERGE INTO t (cols) KEY(id) VALUES (...)` |
| PostgreSQL / SQLite | `ON CONFLICT (id) DO UPDATE SET c = excluded.c`（无更新列 DO NOTHING） |
| Oracle | `MERGE INTO ... USING (SELECT ... FROM dual)` |
| SQL Server | `MERGE ... WITH (HOLDLOCK) USING (VALUES ...)`； |

> SQLite 自 `SqliteDialect` 独立成方言（与 MySQL 分页/引号一致，upsert 语法不同），
> `Dialect.fromDriver/fromUrl/of` 的 sqlite 分流到该实现。
> H2 同理自 `H2Dialect` 独立（分页与 MySQL 一致）：`ON DUPLICATE KEY UPDATE` 与反引号
> 仅 MySQL 兼容模式（`;MODE=MySQL`）可用，故转义改双引号、upsert 用全模式通用的 `MERGE ... KEY`。

### 4.4 事务传播

`@Transactional(propagation = Propagation.REQUIRES_NEW)`：挂起外层事务、开启独立新事务
（独立提交/回滚，结束后外层恢复），适合"无论外层成败都要落账"的审计/计量类操作。
默认 `REQUIRED`（加入或开启）。暂不支持 NESTED（保存点）。

### 4.5 版本化迁移（DatabaseMigration / SchemaMigrator）

替代"DDL 内联代码 + 自造 PRAGMA 迁移"的做法。以 Bean 声明迁移，启动期（容器 refresh
阶段，先于 Web 服务与 ApplicationRunner）按版本升序执行未应用者，成功记入
`summer_schema_history`（默认表名），失败中止启动；重复启动幂等。

```java
@Component
public class V2EmployeeInstance implements DatabaseMigration {
    public int version() { return 2; }
    public String description() { return "员工实例表"; }
    public void migrate(SqlExecutor executor) {
        executor.update(new SqlBuilder.Sql("CREATE TABLE employee_instance (...)", List.of()));
    }
}
```

注意：多数数据库 DDL 隐式提交，单迁移原子性以数据库实际语义为准；注入 `TransactionManager`
时每个迁移独立事务执行。

## 5. AI（summer-ai）

### 5.1 装配解耦 + 运行期模型注册

- **缺配置不再拒启**：`ChatModel`/`ChatClient` 改为懒装配（注入时才实例化）——模型完全由
  应用运行期动态提供时，无需任何 `summer.ai.*` 静态配置；显式 `summer.ai.enabled=false`
  可整体关闭自动装配。
- **`AiModelRegistry` 运行期注册/注销 + 多级回落**：

```java
registry.register("developer-lin", model);           // 运行期热绑定（如按员工实例）
registry.unregister("developer-lin");
registry.resolve("developer-lin", "developer");      // 回落链：实例未绑定 → 角色默认
registry.getOptional(id);                            // 自行决定回落策略
```

### 5.2 用量计量 UsageMeter

维度化记录/聚合 token 消耗（tags 承载 run/stage/role/instance 等维度），
自动装配：有 SqlExecutor 时持久化 `JdbcUsageMeter`（表 `ai_usage`，`summer.ai.usage.table`
可改，惰性建表），否则 `InMemoryUsageMeter`。

```java
meter.record(UsageRecord.of(model, Map.of("run", runId, "instance", "林"), 100, 50));
Map<String, UsageTotal> byInstance = meter.sumGroupedBy("instance", Map.of("run", runId));
UsageTotal runTotal = meter.sum(Map.of("run", runId)); // 实例合计 === 总计（可加性恒等式）
```

## 6. 静态资源服务（boot/web）

控制台前端随 jar 一体化交付：路由优先、静态回退（未命中路由的 GET 才查静态，不与控制器
争路径）。目录穿越防护；按扩展名推断 Content-Type；`/` 映射欢迎页。

```yaml
summer:
  web:
    static:
      enabled: true        # 默认 true，未打包静态资源时零影响
      locations: classpath:/static,classpath:/public,file:./dist
      welcome: index.html
```

## 7. 单实例部署说明（原 B5 降级项）

当前 IdGenerator 为单 JVM 算法、`@Scheduled` 无集群协调——按"单实例 `java -jar`"部署形态
定稿。若将来多副本部署：IdGenerator 需补 worker 位、定时任务需分布式锁、SSE 广播需跨实例
桥接（另立议题）。

## 8. A2A 双轨备查（本期未收口）

summer-ai 的 `ai.agent` A2A（A2aCoordinator/ContractStore/DelegationPolicy + 自动装配）与
agent-pipeline 应用自研 `a2a` 包（HttpClient + 文件契约 + 回调表/轮询双通道）语义并存、
互不引用。收口方向（框架吸收应用语义 / 应用迁移到框架版 / 各自定位）待专项评审；
在此之前两者独立演进，勿混用。
