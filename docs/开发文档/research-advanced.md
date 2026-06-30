# 高级特性研究报告

> 基于 summer 当前实现（ServerSocket + 虚拟线程 + JDK 动态代理 + 纯 JDBC），针对 WebSocket、异步控制器、连接池增强、CGLIB 代理缺失四个方向做可行性分析与实现方案。

## 一、WebSocket 支持

### 1.1 现状与约束

- 当前服务器（`SummerWebServer`）每个连接处理一个 HTTP 请求后即关闭（`Connection: close`），`WebResponse.commit()` 固定写入 `Connection: close` 头；
- `RawHttpRequest.parse()` 只解析单次请求行+头+Content-Length body，无 Upgrade 握手能力；
- **JDK 25 的 `java.net.http.WebSocket` 仅为客户端 API**（通过 `HttpClient.newWebSocketBuilder()` 连接远端），**无服务端 WebSocket 实现**；
- 因此服务端 WebSocket 必须基于 `ServerSocket` 自行实现握手 + 帧协议。

### 1.2 可行性：✅ 可行（纯 JDK）

WebSocket 协议本质是在 HTTP Upgrade 握手后，复用同一 TCP 连接做全双工帧通信。summer 的 `ServerSocket` + 虚拟线程模型天然适合——每个 WebSocket 连接独占一个虚拟线程做阻塞读写，无需 NIO selector。

### 1.3 实现方案

**第一层：Upgrade 握手**

在 `RawHttpRequest` 解析后检测 `Upgrade: websocket` 头 + `Sec-WebSocket-Key`，计算 `Sec-WebSocket-Accept`（SHA-1 拼接 magic GUID），回写 `101 Switching Protocols`。握手后该连接不再走 `RequestDispatcher`，而是交给 `WebSocketSession`。

```
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
→ SHA1(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11") → Base64
→ Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

**第二层：帧协议解析/组装（RFC 6455）**

- 读帧头：FIN(1) + RSV(3) + opcode(4) + masked(1) + payload-len(7/16/64) + masking-key(0/4)；
- opcode：0x1 text、0x2 binary、0x8 close、0x9 ping、0xA pong、0x0 continuation；
- 客户端→服务端帧必须 masked，需用 4 字节掩码 XOR 解码 payload；
- 支持分片（FIN=0 的 continuation 帧拼接）。

**第三层：API 设计**

```java
@WebSocketEndpoint("/ws/chat")
public class ChatEndpoint {
    @OnOpen
    public void onOpen(WebSocketSession session) { ... }

    @OnMessage
    public void onMessage(WebSocketSession session, String message) { ... }

    @OnClose
    public void onClose(WebSocketSession session, CloseReason reason) { ... }

    @OnError
    public void onError(WebSocketSession session, Throwable t) { ... }
}

// 主动推送
session.sendText("hello");
session.sendBinary(bytes);
session.close(1000, "normal");
```

**第四层：集成到现有服务器**

在 `SummerWebServer.handleConnection` 中，解析请求后判断是否为 WebSocket Upgrade：
- 是 → 完成 101 握手 → 创建 `WebSocketSession` → 在**同一虚拟线程**上循环读帧并分发到 `@OnMessage`（阻塞 IO，虚拟线程友好）；
- 否 → 走现有 `RequestDispatcher`。

路由注册：`@WebSocketEndpoint` 由 `WebRouteRegistrar` 扫描，注册到 `Router` 或独立的 `WebSocketRegistry`。

### 1.4 工作量与风险

| 项 | 评估 |
| --- | --- |
| 握手 | ~50 行，标准算法 |
| 帧编解码 | ~200 行，需仔细处理掩码/分片/大 payload |
| Session 管理 | ~100 行（连接池、广播、关闭） |
| 注解扫描集成 | ~80 行 |
| 风险 | 低——纯协议实现，无外部依赖；需测试分片帧和大 payload 边界 |
| 不实现 keep-alive 的影响 | WebSocket 连接本身就是持久的，与 HTTP keep-alive 正交，**不依赖** keep-alive 先行 |

### 1.5 结论

WebSocket 与 summer 架构高度兼容（虚拟线程阻塞模型正是 WebSocket 服务端最优范式），建议作为下一阶段优先项。无需引入第三方库。

---

## 二、异步控制器

### 2.1 现状与约束

- `HandlerMethodInvoker.invoke()` 同步调用控制器方法并返回结果；
- `RequestDispatcher.dispatch()` 同步调用 `writeResult()` 写入响应；
- `SummerWebServer.handleConnection()` 在虚拟线程内同步等待 dispatch 完成后关闭连接；
- 整条链路是「同步阻塞」——但已在虚拟线程上，阻塞不占平台线程。

### 2.2 可行性：✅ 可行（但需评估是否必要）

### 2.3 关键问题：虚拟线程下异步是否多余？

**核心洞察**：summer 每个请求已运行在虚拟线程上，阻塞 IO（查数据库、调外部 API）不占平台线程。Spring MVC 引入异步（`CompletableFuture`/`DeferredResult`）的主要动机是**避免 Servlet 线程池耗尽**——但 summer 没有这个问题，虚拟线程近乎无限。

**因此异步控制器在 summer 中的价值有限**，但仍有两个场景值得支持：

1. **聚合多个并行 IO**：用 `CompletableFuture` 并发发起多个独立调用，`allOf` 等待后聚合，比串行更快；
2. **返回 `CompletableFuture` 的自然集成**：开发者已有返回 `CompletableFuture` 的服务方法，控制器直接透传无需 `.join()`。

### 2.4 实现方案

在 `RequestDispatcher.writeResult()` 中增加对 `CompletableFuture` 的检测：

```java
if (result instanceof CompletableFuture<?> cf) {
    // 方案 A：阻塞等待（最简单，仍在虚拟线程上，不占平台线程）
    writeResult(route, cf.join(), response);
    return;
}
```

**方案 A（阻塞 join）**——改动最小（~5 行），在虚拟线程上 `join()` 不占平台线程，行为正确。适合 v1。

**方案 B（非阻塞回调）**——不阻塞虚拟线程，注册 `whenComplete` 回调异步写响应。需重构 `handleConnection` 不在 finally 关闭 socket，改为回调驱动关闭。复杂度高，且虚拟线程下收益不明显。

**推荐方案 A**：最小改动、行为正确、虚拟线程下零代价。

### 2.5 对其他返回类型的扩展

可顺带支持：

| 返回类型 | 处理 |
| --- | --- |
| `CompletableFuture<T>` | `cf.join()` 后按 T 序列化 |
| `HttpResponse<T>`（自定义） | 显式控制 status + headers + body |

### 2.6 结论

异步控制器在虚拟线程框架中**不是刚需**，但支持 `CompletableFuture` 返回只需 ~5 行改动，建议用方案 A 轻量实现，主要服务于「并行聚合」场景。

---

## 三、连接池增强

### 3.1 现状分析

当前 `DataSourceFactory.PooledDataSource`：

| 能力 | 现状 | 问题 |
| --- | --- | --- |
| 池大小 | 固定 `poolSize`，`ArrayBlockingQueue` | 无动态扩缩 |
| 借出校验 | `isValid(2)` | ✅ 有，但 2 秒超时偏长 |
| 归还校验 | 无 | 死连接可能留在池中 |
| 空闲超时 | 无 | 连接永不回收，DB 侧可能已断开 |
| 最大空闲 | 无 | 同上 |
| 泄漏检测 | 无 | 忘记 close 的连接永久丢失 |
| 等待超时 | `pool.take()` 无限阻塞 | 池耗尽时永久挂起 |
| 自动重连 | 借出时发现失效才重建 | ✅ 基本可用 |
| 事务连接 | `TransactionManager` 用 ThreadLocal 绑定 | 事务期间不归还，事务结束才归还 ✅ |

### 3.2 关键风险

1. **`pool.take()` 无超时**：池满时新请求无限阻塞，虚拟线程虽不占平台线程但请求永不超时；
2. **无泄漏检测**：`SqlExecutor` 用 try-with-resources 保证归还，但若开发者绕过框架直接 `dataSource.getConnection()` 且忘记 close，连接永久丢失；
3. **无空闲回收**：长时间空闲的物理连接可能被 DB 侧或网络中间件断开（如 Supabase 的连接超时），下次 `isValid` 才发现；
4. **虚拟线程与 pinning**：`ArrayBlockingQueue.take()` 是 synchronized 阻塞——在 JDK 25 中 `synchronized` 仍可能 pin 虚拟线程（JDK 24+ 已大幅改善但仍非完全消除）。改用 `ReentrantLock` + `Condition` 可避免 pinning。

### 3.3 增强方案（按优先级）

**P0：借出超时**

```java
Connection conn = pool.poll(timeout, TimeUnit.MILLISECONDS);
if (conn == null) throw new SQLException("Connection wait timeout after " + timeout + "ms");
```

配置项 `summer.datasource.connection-timeout: 30000`。

**P1：泄漏检测**

借出时记录调用栈 + 时间戳到 `WeakHashMap`；后台虚拟线程定期扫描超时未归还的连接，打 WARN 日志（含借出栈），可选自动关闭回收。

```java
private final Map<Connection, LeaseInfo> leases = new ConcurrentHashMap<>();
record LeaseInfo(long borrowedAt, StackTraceElement[] stack) {}
```

配置项 `summer.datasource.leak-detection-threshold: 60000`（60 秒未归还视为泄漏）。

**P2：空闲连接保活 + 回收**

后台虚拟线程定期（如每 30 秒）遍历池中连接：
- 空闲超过 `max-idle` → 关闭移除；
- 执行 `isValid(1)` 或 `SELECT 1` 保活（可选 `keepalive-query`）。

**P3：避免虚拟线程 pinning**

将 `ArrayBlockingQueue` 替换为基于 `ReentrantLock` + `Condition` 的有界队列，或使用 `java.util.concurrent.LinkedBlockingQueue` 配合容量控制。

**P4：动态扩缩**

`min-size` / `max-size`：池低于 min 时自动补充，高于 min 的空闲连接按 P2 回收。

### 3.4 建议配置

```yaml
summer:
  datasource:
    pool-size: 8                # → 拆分为 min-idle / max-size
    min-idle: 2
    max-size: 16
    connection-timeout: 30000   # 借出等待超时
    idle-timeout: 600000        # 空闲超时回收（10 分钟）
    max-lifetime: 1800000       # 连接最大生存期（30 分钟强制重建）
    leak-detection-threshold: 60000  # 泄漏检测阈值
    keepalive-query: "SELECT 1" # 保活查询（可选）
```

### 3.5 结论

P0（借出超时）+ P1（泄漏检测）应优先实现，直接关系生产稳定性。P2-P4 可迭代。总工作量约 300-400 行，仍是纯 JDK。

---

## 四、子类代理（无 CGLIB 依赖）

### 4.1 背景与决策

summer 的 AOP 与 `@Transactional` 最初仅基于 **JDK 动态代理**（`AdvisedProxyFactory` → `Proxy.newProxyInstance`），核心限制是**目标类必须实现至少一个接口**才能被代理。CGLIB 通过生成子类绕过此限制，但它是第三方库，违背 summer 零第三方依赖原则。

历史上曾采用「无接口时显式报错」的过渡策略（方案 A），推动开发者面向接口编程。现已**自研子类代理**（方案 B 的工程实现），在保持零依赖的前提下，让无接口、非 `final`、构造器实例化的 bean 也能被 `@Transactional`/`@Aspect` 代理。

### 4.2 实现机制

代理策略**自动判断**，无需配置：

- **有接口** → JDK 动态代理（行为不变，零回归）
- **无接口且可继承**（非 `final`、通过构造器实例化）→ 手写字节码子类代理

子类代理的核心在 `summer-core` 的 `aop/bytecode/` 包，由 `ClassBuilder` 直接拼装 JVM class 文件（major 69 = Java 25），`ProxyClassLoader` 用 `defineClass` 加载：

- **override 方法**：每个 public/protected 非 final 方法被 override，方法体委托给 `SubclassProxyFactory.intercept(this, methodIndex, args)`，进入拦截器链
- **桥接方法** `$$summer$super$<方法>`：以 `invokespecial super.<方法>` 调用父类原始实现，作为拦截器链的**链尾**——这破解了自调用递归陷阱（`a()` 内部调 `b()`，二者都被拦截且不无限递归）
- **单对象模型**：代理实例即 bean 本身，字段在代理实例内，`getThis()`/`getTarget()` 指向同一代理
- **免 StackMapTable**：所有生成方法体保持纯线性（无跳转/无 try-catch），故无需计算栈帧——这是手写字节码能落地的关键前提

### 4.3 对各层的影响

| 层 | 是否需要代理 | 无接口时 |
| --- | --- | --- |
| Controller | ❌ 不需要 | 无影响（反射直接调用） |
| Service | ✅ 可能需要（`@Transactional`/AOP） | **子类代理生效** |
| Repository/Mapper | ❌ 不需要（已是 JDK 代理生成的接口实现） | 无影响 |
| Component | ✅ 可能需要（AOP） | **子类代理生效** |

### 4.4 已知限制

- 仅拦截 public/protected 非 final 方法；`final` 方法/类、private/static 方法不拦截
- `final` 类无法子类代理（抛 `BeansException`）
- `@Bean` 工厂方法 / `instanceSupplier` 产生的无接口 bean 暂不支持子类代理（需提取接口）
- 应避免在构造器中调用可被拦截的方法（构造期拦截尚未就绪）
- 模块化应用（named module）需 `--add-opens` 才能定义同包代理类；summer 按 classpath 运行不受影响
- v1 未做 FastClass 优化，空链也经 dispatch 反射，有反射开销

### 4.5 与方案 A（显式报错）的关系

方案 A 作为过渡期策略已完成其历史使命（消除静默失效隐患）。自研子类代理上线后：

- 无接口 + 非 final + 构造器实例化 → 自动子类代理，事务/AOP 生效
- 无接口 + final 类 / 工厂方法 → 仍抛 `BeansException`（明确告知不可代理的原因与解法）
- 这比静默失效或一刀切报错都更友好：能代理的自动代理，不能的显式报错

---
## 五、优先级建议

| 特性 | 价值 | 复杂度 | 建议优先级 |
| --- | --- | --- | --- |
| 无接口子类代理 | 高（消除静默失效隐患） | 中高（~1000 行手写字节码） | ✅ 已实现 |
| 连接池借出超时 | 高（生产稳定性） | 低（~20 行） | ✅ 已实现 |
| 连接池泄漏检测 | 中高 | 中（~100 行） | ✅ 已实现 |
| 异步控制器（方案 A） | 中（并行聚合场景） | 极低（~5 行） | ✅ 已实现 |
| WebSocket | 高（微服务常用） | 中高（~400 行） | P2 |
| 连接池空闲保活/动态扩缩 | 中 | 中（~200 行） | P2 |
| 异步控制器（方案 B） | 低（虚拟线程下收益小） | 高 | 不建议 |
