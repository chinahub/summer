# 代码审查报告

> 审查时间: 2026-06-23
> 范围: summer-boot / summer-core / summer-data / summer-web 全部模块
> 注: 审查时项目使用 JPMS 模块化，后续已移除 JPMS，改为 classpath + 可执行 jar 方式运行（见 usage.md）
> 目标: 找出 bug、性能问题、线程安全风险

整体评价: 作为学习/玩具项目完成度相当高 (零依赖、JPMS、虚拟线程、自动装配、AOP、ORM、WebSocket 都齐了),距离生产可用主要差在几个会出事故的点上。

---

## 严重 Bug

### #1 `SqlBuilder.replacePropertyWithColumn` 的属性名替换是字符串字面替换,会误伤

**文件:** `summer-data/.../SqlBuilder.java:161-166`

```java
private String replacePropertyWithColumn(String segment, String property, String column) {
    String regex = "\\b" + java.util.regex.Pattern.quote(property) + "\\b";
    return java.util.regex.Matcher.quoteReplacement(column) == null
            ? segment : segment.replaceAll(regex, java.util.regex.Matcher.quoteReplacement(column));
}
```

- `Matcher.quoteReplacement` 永远返回非 null (对 null 输入抛异常),所以三元表达式 `== null ?` 永远是 false。
- 更严重的问题: 整个 `replacePropertyWithColumn` 对 wrapper 没用。`AbstractWrapper.column()` 返回的就是 `reference` 本身 (LambdaQueryWrapper/QueryWrapper 直接透传),**segments 里根本没有 Java 属性名**,全是 SQL 列名,所以这段代码永远在做无效的 `regex.replaceAll`。
- 此外 `\\b` 在 `user_name` 这种带下划线的 SQL 列名上行为不稳定。
- **修复**: 删除该方法或先确认 wrapper 里是 property 名还是 column 名。

---

### #2 JSON 反序列化 BigDecimal 失精 / 查询整行失败

**文件:** `summer-web/.../Json.java:534`、`summer-data/.../SqlExecutor.java:154-170`

```java
// Json.java
return floating ? Double.valueOf(token) : Long.valueOf(token);

// SqlExecutor.coerce
if (value == null) return null;
if (targetType.isInstance(value)) return value;
String s = value.toString();
if (targetType == String.class) return s;
...
```

- JSON 数字统一先 parse 成 `Long`/`Double`,`bind()` 里再 coerce。**对大于 2^53 的 ID 会丢精度** (`Long` → `Integer` 强转时不会报错而是截断)。
- JDBC 驱动常常把数值类型返回为 `BigDecimal`。`mapRows` 里 `targetType.isInstance(value)` 对 `Long.class` 匹配 `BigDecimal` 是 false,会落到 `value.toString()` 再转回 `Long`。这条路径在 `Long.parseLong` 时没有 number-format 的捕获,直接抛 `NumberFormatException`,**导致整行查询失败**。
- **修复**: `coerce` 中增加 `BigDecimal`/`BigInteger` 分支,并把 toString 转换包在 try/catch 里返回 null。

---

### #3 `WebResponse` Connection 头大小写不一致可能双写

**文件:** `summer-web/.../WebResponse.java:60`

```java
headers.putIfAbsent("Connection", keepAlive ? "keep-alive" : "close");
```

- HTTP/1.0 中 `Connection: keep-alive` 是显式的; HTTP/1.1 中 `Connection: close` 才对。
- `WebResponse.body()` 无 max-size 限制,**会被恶意大请求 DoS**。
- 如果用户在 `header("connection", ...)` 里塞了大小写不一致的 key,就会被 put 进去 (因为 `LinkedHashMap.put` 用新 key),响应里就出现两条 `Connection` 头。
- **修复**: 加 `server.max-request-size` 限制; `Connection` 头 key 固定为 `Connection`(不区分大小写去重)。

---

### #4 `MapperSupport.insert` 回填 generated key 失败时未清理半成品实体

**文件:** `summer-data/.../MapperSupport.java:33-42`

```java
public int insert(T entity) {
    fillIdIfNeeded(entity);  // 已写入 id
    SqlBuilder.Sql sql = sqlBuilder.insert(entity);
    SqlExecutor.UpdateResult result = executor.updateWithGeneratedKey(sql, table);
    ...
}
```

- 若 SQL 报错,**实体已经被填充了客户端传入前的 null ID** (用户传入的对象被改写),然后抛 `DataAccessException`。
- 下次重试时 `fillIdIfNeeded` 看到 ID 不为 null 就不再生成,但数据库侧已经因为事务回滚没记录,业务上 ID 是脏的。
- 影响较小但确实存在。
- **修复**: 不预填 ID,而是依赖数据库的 `INSERT ... RETURNING`(方言相关); 或保留现状但文档说明。

---

### #5 `RawHttpRequest.readHeaderBlock` 没有 header size 上限

**文件:** `summer-web/.../RawHttpRequest.java:74-94`

```java
private static byte[] readHeaderBlock(InputStream in) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream(512);
    int[] win = new int[4];
    int n = 0;
    int b;
    while ((b = in.read()) != -1) {
        buf.write(b);
        ...
    }
    ...
}
```

- 滑动窗口检测 `\r\n\r\n` 本身逻辑正确。
- **真正的 bug**: 没有 header size 上限,会被慢速客户端发超大 header 占内存。
- **修复**: 设定 `server.max-header-size`(默认 16KB),超过直接断连。

---

### #6 `DailyRollingFileHandler.purgeOlderThan` 文件名解析错误

**文件:** `summer-core/.../DailyRollingFileHandler.java:140-155`

```java
for (String part : parts) {
    try {
        LocalDate fileDate = LocalDate.parse(part, DATE_FORMAT);
        if (fileDate.isBefore(cutoff)) {
            Files.deleteIfExists(p);
        }
        break;
    } catch (Exception ignore) {}
}
```

- 文件名格式是 `app.2026-06-23.log` 或 `app.2026-06-23.1.log`,`split("\\.")` 得到正确分段。
- **真正的 bug**: `break` 在第一个能 parse 的 part 上执行。`parts[1]` 不是日期时(比如文件被其他工具重命名),**会错误地跳过删除**; 如果文件被命名为 `app.2026-06-23.txt`,也会被错误解析并删除(尽管不是日志文件)。
- **修复**: 要求 part 是精确 `yyyy-MM-dd` 形式且整名匹配前缀规则,不要 try-parse 字符串片段; 或者用正则严格匹配。

---

### #7 `ClassPathScanner.scanDirectory` 多 root 重复 walk

**文件:** `summer-core/.../ClassPathScanner.java:81-96`

```java
for (Path root : collectRoots()) {
    for (String pkg : basePackages) {
        String path = pkg.replace('.', '/');
        if (Files.isDirectory(root)) {
            scanDirectory(root.resolve(path), pkg, classLoader, classes);
        } else if (root.toString().endsWith(".jar") && Files.exists(root)) {
            scanJar(root, path, pkg, classLoader, classes);
        }
    }
}
```

- `collectRoots` 已经把 module-path 的根拆开,这个开销是 **O(roots × packages)**。
- `scanDirectory` 用 `Files.walk` 对每个 root+pkg 组合做全树遍历,**如果多个 root 都包含同名包,每个 root 都会触发一遍 walk**。
- **修复**: 先 `Files.find` 一次性收集所有匹配目录,避免重复 walk。

---

### #8 `Router.match` 是 O(n) 线性扫描,大路由表性能差

**文件:** `summer-web/.../Router.java:23-32`

```java
public Optional<RouteMatch> match(HttpMethod method, String path) {
    for (RouteMapping route : routes) {
        if (route.httpMethod() != method) continue;
        var vars = route.pattern().match(path);
        if (vars.isPresent()) {
            return Optional.of(new RouteMatch(route, vars.get()));
        }
    }
    return Optional.empty();
}
```

- 每个请求线性扫描所有路由。Spring 用 trie 树。
- 同时 `RoutePattern.match` 每次都重新 split + LinkedHashMap。
- **修复** (考虑项目定位): 按 method 分桶 + 按段数分桶,然后线性扫描同段数桶; `RoutePattern` 预编译正则。

---

### #9 `writeNoRoute` 在每次无路由匹配时遍历全部 routes

**文件:** `summer-web/.../RequestDispatcher.java:113-114`

```java
boolean pathExists = router.routes().stream()
        .anyMatch(r -> r.pattern().match(path).isPresent());
```

- 如果首轮 `router.match` 失败,再二次遍历所有路由只为确认"是否只是 method 不对",这个二次扫描对每次 404 都执行。
- **修复**: 首轮 match 时就记录哪些路径存在但 method 不匹配。

---

## 中等问题

### #10 `PointcutMatcher.patternToRegex` 没缓存正则

**文件:** `summer-core/.../PointcutMatcher.java:74-92`

- 每次方法调用都重新 `Pattern.compile`。`buildChain` 在每次代理方法调用都会跑。
- **修复**: 用 `Map<String, Pattern>` 缓存。

---

### #11 `AdvisedProxyFactory.createProxy` 每次调用都 buildChain

**文件:** `summer-core/.../AdvisedProxyFactory.java:43`

```java
List<MethodInterceptor> chain = buildChain(target.getClass(), method, interceptors, advices);
```

- 每次方法调用都执行 `buildChain`,扫描所有 advices + 正则匹配。**对一个有 N 个 advice 的 bean 来说单次方法调用是 O(N × advices)**。
- **修复**: 在 `needsSubclassProxy`/`maybeWrapInJdkProxy` 里按 `Method` 预构建 chain,或者缓存最近 N 个方法。

---

### #12 `populateBean` 用 `getMethods()` 而非 `getDeclaredMethods()`,会扫到 Object 方法

**文件:** `summer-core/.../DefaultApplicationContext.java:283`

```java
for (Method method : bean.getClass().getMethods()) {
    if (method.isAnnotationPresent(Autowired.class) && method.getParameterCount() > 0) {
```

- `getMethods()` 含桥接方法 + Object 方法 (无 @Autowired,通常不会进入分支),但每次都遍历。
- **修复**: 用 `ReflectionUtils.collectFields` 那种风格只扫 declared + 父类。

---

### #13 `DefaultApplicationContext.preInstantiateSingletons` 顺序假设 aspects 一定是 advisor

**文件:** `summer-core/.../DefaultApplicationContext.java:121-132`

```java
for (String name : aspects) getBean(name);
collectAopRegistries();
for (String name : others) getBean(name);
```

- 假设 aspects **不依赖任何普通 bean**,如果 `@Aspect` 类 `@Autowired` 了一个 service,**会因 `others` 还没初始化而失败**。
- 更隐蔽的是 `others` 阶段反过来也依赖 `aspects` 提供的 advisor。当前顺序看似工作,但任何 `@Aspect` 注入 service 都会炸。
- **修复**: 按真实依赖拓扑排序,或两轮扩展 (第一轮只注册 advice,不实例化; 第二轮正常)。

---

### #14 ✅ `DataSourceFactory.PooledDataSource` housekeeping 可能把池抽干（已修复）

> ✅ 已修复（HikariCP 风格重写）：max-lifetime 每条连接随机抖动 ±2.5%、housekeeper 关闭后按 `minimum-idle` 补建、`borrow()` 池空时按需懒创建——池不再被抽干到零、无需重启。详见 `DataSourceFactory.java`。下为原始分析。

**文件:** `summer-data/.../DataSourceFactory.java:168-189`

```java
List<PooledConnection> drain = new ArrayList<>();
pool.drainTo(drain);  // 把池里全部连接抽走
for (PooledConnection pc : drain) {
    boolean keep = true;
    if (maxLifetime > 0 && (now - pc.createdAt) > maxLifetime) keep = false;
    if (keep && idleTimeout > 0 && (now - pc.returnedAt) > idleTimeout) {
        int current = pool.size() + drain.size() - 1;
        if (current >= 1) keep = false;
    }
    ...
}
```

- `int current = pool.size() + drain.size() - 1` —— 当池初始 1 个连接时,`current = 0 + 1 - 1 = 0`,**第一个 idle 连接直接被关掉**,下次请求就要阻塞等待新连接 (`borrow` 在 `newConnection` 失败/慢时会卡死)。
- **修复**: 保留至少 `min-idle` 个,或仅在 `pool.size() > min-idle` 时才关闭。

---

### #15 `PooledConnectionHandler` 不代理 `unwrap`/`isWrapperFor` 真实语义

**文件:** `summer-data/.../DataSourceFactory.java:324-327`

```java
if (name.equals("unwrap") && args != null && args.length == 1) {
    Class<?> iface = (Class<?>) args[0];
    if (iface.isInstance(pc.raw)) return pc.raw;
}
```

- `unwrap` 没有返回这个 `iface` 的代理实例,而是返回原始连接 —— 用法 OK,但 `PooledDataSource.unwrap` 自己 throw,**结合来看语义不一致**。
- **修复**: 统一 `unwrap` 语义,或明确文档说"summer pool 不支持 unwrap"。

---

### #16 `Environment.resolvePlaceholders` 递归展开未实现,默认值无 trim

**文件:** `summer-core/.../Environment.java:107-146`

```java
String key;
String defaultValue = null;
int colon = expr.indexOf(':');
if (colon >= 0) {
    key = expr.substring(0, colon);
    defaultValue = expr.substring(colon + 1);
} else {
    key = expr;
}
```

- `${key:default value with space}` —— default 不 trim,会带尾部空格。
- **不支持嵌套**: `${outer:${inner}}` 这种 Spring 支持的形式不能用。
- **修复**: trim + 简单嵌套展开 (限制 5 层防循环)。

---

### #17 `WebSocketSession.runLoop` 不支持 fragment + 不校验 mask

**文件:** `summer-web/.../WebSocketSession.java:222-278`

- 单字节 read 解析 header,慢但正确。
- **没有分片(fragmentation)处理**: `fin=false` 的连续帧会被丢弃 (默认 switch case 走到 unknown)。
- **mask 校验缺失**: WebSocket 规范要求客户端发送的帧必须 mask,这里信任客户端,**不符合 RFC 6455**。
- **修复**: 实现 fragment reassembly,严格校验 mask。

---

### #18 `Json.writeString` 的 surrogate pair 处理有缺陷

**文件:** `summer-web/.../Json.java:194-216`

- 对 BMP 外的字符 (JSON 中以 `😀` 形式或裸码点),目前直接写 char (可能写半个 surrogate)。
- **修复**: 检测 surrogate pair,要么转 `\uXXXX\uXXXX`,要么直接写码点 (需要 sb.appendCodePoint)。

---

### #19 `Validator.format` 替换规则错误

**文件:** `summer-web/.../Validator.java:105-110`

```java
private static String format(String message, Object a, Object b) {
    String result = message.replace("{value}", String.valueOf(a));
    if (a != null) result = result.replace("{min}", String.valueOf(a)).replace("{regexp}", String.valueOf(a));
    if (b != null) result = result.replace("{max}", String.valueOf(b));
    return result;
}
```

- 三个调用点: `format(size.message(), size.min(), size.max())`、`format(min.message(), min.value(), null)`、`format(p.message(), p.regexp(), null)`。
- **第 3 个调用把 regexp 作为 `a` 传入,但代码同时替换 `{value}` 和 `{regexp}`**; 如果 min 的 message 模板意外含 `{regexp}` 文字,会被换掉。
- **修复**: 每个约束独立写 format,或按约束类型分派。

---

### #20 `HandlerMethodInvoker.bindModelAttribute` 字段注入绕过 setter

**文件:** `summer-web/.../HandlerMethodInvoker.java:152-169`

- 直接 `field.set(model, ...)`,不经过 setter,**可能导致 JSR-303 validation 跳过** (`@AssertTrue` 方法不触发),且破坏 `@JsonIgnore` 反向序列化。
- 警告而非阻塞,但需文档说明。

---

### #21 `RoutePattern` 不支持 `{var:regex}` (带约束的路径变量)

- 已知 Spring/Tomcat 都支持,如 `/users/{id:\d+}`。这是功能缺失,非 bug。

---

## 性能/风格

### #22 `Json.writeObject` 每次序列化都反射遍历所有方法

- **修复**: 在第一次序列化后缓存字段集合 (类似 Jackson 的 `JsonSerializer`),或要求用户显式加 `@JsonProperty`。

---

### #23 `LoggingInitializer.initialize` 不幂等

- 重复调用会重复添加 handler (虽然 `removeSummerHandlers` 会清掉 Tag 标记的,但应用代码加的非 tagged handler 不会被清理)。
- 这通常只在测试中触发,但仍是隐患。

---

### #24 `ScheduledTaskRegistrar` 单线程调度器,所有定时任务共享一个线程

- 单点瓶颈,一个慢任务会推迟后续任务 (`scheduleAtFixedRate` 会 catch-up,`scheduleWithFixedDelay` 不会)。
- 文档建议设 `scheduler.setRemoveOnCancelPolicy(true)` 已做了。

---

### #25 `DailyRollingFileHandler.publish` 用 synchronized,锁粒度粗

- 多线程并发写日志会串行化,在高 QPS 下可能成为瓶颈。
- **修复**: 用 `ConcurrentLinkedQueue` 收集 + 单写线程,或 `synchronized` 改为 `ReentrantLock`。

---

### #26 JDBC PreparedStatement 每次都新建,无 statement 缓存

- JDBC 自身有 prepared statement caching (若 URL 启用),但代码层面没有池化。
- 项目定位是"极简",可接受。

---

### #27 `DsContext.current()` 在虚拟线程上 ThreadLocal 抖动

- 虚拟线程复用载体线程,ThreadLocal 在虚拟线程上不连续,会创建/销毁更频繁。
- 微不足道,但在 million 级请求下可能有性能影响。

---

### #28 `DefaultApplicationContext.getBeanNamesForType` O(n) 且每次都调 `getType`

**文件:** `summer-core/.../DefaultApplicationContext.java:362-370`

```java
public String[] getBeanNamesForType(Class<?> type) {
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, BeanDefinition> e : beanDefinitions.entrySet()) {
        Class<?> bt = getType(e.getKey());
        if (bt != null && type.isAssignableFrom(bt)) result.add(e.getKey());
    }
    return result.toArray(String[]::new);
}
```

- 每次依赖解析都扫所有 bean 定义。Spring 用 `Map<Class, List<String>>` 缓存。
- **修复**: 启动时建 `Map<Class, List<String>>` 索引 (注意代理类的处理)。

---

### #29 `Router.routes` 返回原始 `List`,外部可写

**文件:** `summer-web/.../Router.java:21`

- 暴露可变内部状态。`sortBySpecificity` 后就锁了,但用户调 `register` 后会破坏排序。
- **修复**: 返回 `Collections.unmodifiableList` 或 `List.copyOf`。

---

### #30 `WebRouteRegistrar.descriptorsFor` 对元注解做了 fallback 但未对所有 HTTP method 注解做

**文件:** `summer-web/.../WebRouteRegistrar.java:88-97`

```java
if (list.isEmpty()) {
    if (AnnotationUtils.hasAnnotation(method, RequestMapping.class)) {
        RequestMapping meta = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        ...
    }
}
```

- 用户写 `@GetMapping` 的元注解 (自定义组合注解) 时不会被识别,因为元注解查找只对 `RequestMapping` 做。`@PostMapping` 等元注解不生效。

---

## 线程安全/资源

### #31 `Router`/`RouteMapping`/`RoutePattern` 不可变,OK

### #32 `DefaultApplicationContext` 用 `ConcurrentHashMap` + `synchronizedList`,OK 但有 race

- `destructionOrder` 是 `synchronizedList`,OK; `earlySingletonObjects` 没有锁保护并发 getBean,但**单线程 refresh** 场景下没问题。
- 真实风险: `aspectRegistry.registerAspect` 在 `collectAopRegistries` 里只触发一次,且 `aspects` 先实例化,期间不会有人调 getBean (其他),**假设下安全**。注释里需要明确"容器 refresh 期间单线程"。

---

### #33 `DsContext`/`DsTransactionManager` 的 ThreadLocal 在虚拟线程上不清理会泄露

- `DsContext.pop` 在 finally 里调,OK; 但 `DsTransactionManager.end` 后清 ThreadLocal,**若中途抛异常且 finally 之前没跑到 `end()`,HOLDER 永远挂在那**,直到线程死亡。虚拟线程复用会累积。
- **修复**: `begin` 处记一个 `try { ... } finally { clear }` 包装; 或在请求入口 servlet filter 处强制清理。

---

### #34 `TransactionManager` 同名 ThreadLocal 在嵌套方法里不感知 PROPAGATION

- `begin` 时如果已有 stack,直接 `return false` 加入现有事务。如果用户期望 `REQUIRES_NEW`,**项目根本没实现传播语义** (注释里也说了"reference-counted stack"),这是设计选择,但要文档化清楚。
- 而且 `end` 只在 `began=true` 时关闭连接,嵌套调用者永远不会关栈底连接,看起来 OK,但 `end` 后没 pop 栈之后的 entries (实际上只有一项)。

---

## 配置/部署

### #35 `ServerSocket` 没设 `setReuseAddress(true)`

- **修复**: 在 `bind` 前调用,避免重启时 TIME_WAIT 端口冲突。

---

### #36 `acceptLoop` 在 `accept` 抛 IOException 后立即 break

**文件:** `summer-web/.../SummerWebServer.java:83-96`

- 如果是瞬时错误 (EMFILE 之类),循环就死了。
- **修复**: 短暂 backoff 后重试。

---

### #37 `ShutdownHook` 用 `shutdownNow` + `awaitTermination(2s)`

**文件:** `summer-web/.../SummerWebServer.java:208-215`

- 2 秒太短,长事务可能被截断。
- **修复**: 可配置或长一些 (5-10s)。

---

## 优先级建议

| 优先级 | 项 | 说明 |
|---|---|---|
| P0 | #2 JSON BigDecimal 处理 | 数据丢失/查询失败 |
| P0 | #1 SqlBuilder 错误替换 | SQL 注入风险 + 性能浪费 |
| ✅ | #14 连接池抽干 | 已修复（HikariCP 风格：抖动+补建+懒创建） |
| P1 | #6 DailyRolling 删除 | 数据丢失 |
| P1 | #17 WebSocket mask/fragment | 协议合规 |
| P1 | #3 Connection 头 | 客户端兼容 |
| P1 | #16 Placeholder 默认值 | 配置错误难排查 |
| P2 | #8 #9 #28 路由/依赖解析性能 | 规模化后瓶颈 |
| P2 | #10 #11 AOP 缓存 | 每次方法调用的开销 |
| P3 | 风格类 | 长期可维护性 |

---

## 总结

作为零依赖的 Spring Boot 极简替代,summer 项目在结构设计、模块拆分、虚拟线程友好性上完成度相当高,连接池鲁棒性已通过 HikariCP 风格重写基本解决（#14 已修复：max-lifetime 抖动 + minimum-idle 保活补建 + 借出懒创建自愈 + keepalive 探活），当前主要差距在数据完整性、协议合规性这两块。要进入生产,优先修复剩余 P0 两项即可消除最致命的事故源。
