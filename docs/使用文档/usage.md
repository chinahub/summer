# 使用

## 构建

```powershell
$env:JAVA_HOME='D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;D:\mvnd-1.0.5\mvn\bin;" + $env:Path
mvn -s E:\summer_workspace\settings.xml -o clean package
```

产出各模块 `target/*.jar`。

## 运行（可执行 jar / java -jar）

summer 打成 Spring Boot 风格的可执行 jar：依赖以**整 jar 形式**嵌在 `BOOT-INF/lib/`，应用类放在 `BOOT-INF/classes/`，jar 根放启动器，`META-INF/MANIFEST.MF` 写入 `Main-Class` 与 `Start-Class`。与 `maven-shade-plugin` 把所有 class 爆开进一个 jar 不同，这里保留依赖 jar 不拆解。

布局：
```
summer-sample-3.0.0.jar
├─ cn/jiebaba/summer/loader/JarLauncher.class   # 启动器（jar 根）
├─ BOOT-INF/classes/...                          # 应用类与资源（application.yml）
├─ BOOT-INF/lib/*.jar                            # 依赖 jar（summer-* / postgresql 等）
└─ META-INF/MANIFEST.MF                          # Main-Class / Start-Class
```

构建（`mvn package` 自动触发 `summer-pack-maven-plugin` 的 `repackage`，一步产出可执行 jar）：
```powershell
$env:JAVA_HOME='D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;" + $env:Path
mvn -s E:\summer_workspace\settings.xml -o clean package
```
产出 `summer-sample/target/summer-sample-3.0.0-boot.jar`（可执行 jar）。`classifier` 默认为 `boot`，可执行 jar 作为独立 `-boot` 产物，主产物 `summer-sample-3.0.0.jar` 保持 thin jar 供 `build-test` 等模块依赖编译。

运行：
```powershell
java -jar summer-sample\target\summer-sample-3.0.0-boot.jar
```

> 打包插件：`summer-sample` 的 `pom.xml` 绑定了 `summer-pack-maven-plugin:repackage`（绑定 `package` 阶段），故 `mvn package` 自动产出可执行 jar。默认 `classifier=boot`，产出独立的 `<finalName>-boot.jar`，主产物 `<finalName>.jar` 保持 thin jar 不变，可被其他模块依赖；若终端应用不需要被依赖、想要单文件，可设 `<classifier></classifier>`（空）替换主产物，原 thin jar 备份为 `<finalName>.jar.original`。`startClass` 在 `pom.xml` 的 `<configuration>` 中配置。

> 原理：`summer-boot-loader` 的 `JarLauncher` 是 `Main-Class`（由 `summer-pack-maven-plugin` 内置打包，应用无需声明该依赖），启动时把 `BOOT-INF/classes`、`BOOT-INF/lib/*.jar` 解压到临时目录，重建 `java.class.path`，再用 `URLClassLoader` 加载并调用 `Start-Class` 的 `main`。之所以解压而非用 `jar:...!/BOOT-INF/...` 嵌套 URL，是因为 summer 的 `ClassPathScanner` 直接读 `java.class.path` 系统属性来枚举类，不遍历 ClassLoader 的 URL。
## 打包插件配置

`summer-pack-maven-plugin` 的 `repackage` 目标负责把应用打成可执行 jar，只需配置 `startClass`，无需声明 `summer-boot-loader` 依赖（插件已内置）。

### 最小配置

```xml
<build>
    <plugins>
        <plugin>
            <groupId>cn.jiebaba.summer</groupId>
            <artifactId>summer-pack-maven-plugin</artifactId>
            <version>3.0.0</version>
            <executions>
                <execution>
                    <goals><goal>repackage</goal></goals>
                    <configuration>
                        <startClass>cn.jiebaba.kanban.Application</startClass>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

绑到 `package` 阶段后，`mvn package` 自动产出可执行 jar `target/<finalName>-boot.jar`，再用 `java -jar` 运行。

### 配置参数

| 参数 | 必填 | 默认值 | 写入 manifest | 说明 |
| --- | --- | --- | --- | --- |
| `startClass` | 是 | — | `Start-Class` | **应用启动类**全限定名，`JarLauncher` 反射调用它的 `main` |
| `mainClass` | 否 | `cn.jiebaba.summer.loader.JarLauncher` | `Main-Class` | jar 根启动器，`java -jar` 首先执行的类，几乎不用改 |
| `classifier` | 否 | `boot` | — | 默认 `boot`，产出独立的 `<finalName>-boot.jar`，主产物 thin jar 保留供其他模块依赖；设为空则替换主产物 `<finalName>.jar`，原 thin jar 备份为 `<finalName>.jar.original`（适用于不被依赖的终端应用） |

**两者关系**：`java -jar` 读 `Main-Class` 跑 `JarLauncher`（启动器，来自内置的 `summer-boot-loader`），`JarLauncher` 再读 `Start-Class` 定位你的应用主类并调用其 `main`。因此应用只需关心 `startClass`。

### 无需声明 summer-boot-loader

与 `spring-boot-maven-plugin` 内置 `spring-boot-loader` 相同，`summer-boot-loader`（含 `JarLauncher`）是 `summer-pack-maven-plugin` 的**插件依赖**，打包时从插件自身 classpath 读取并写入 jar 根。应用项目的 `pom.xml` **不需要**也**不应该**声明 `summer-boot-loader` 或 `summer-loader`，否则会被自动过滤，避免重复打入 `BOOT-INF/lib`。

> 为何 `summer-boot-loader` 独立成模块而非并入插件，设计动机见 [architecture.md](../开发文档/architecture.md)。

### 典型依赖

应用项目只需声明 `summer-boot` 即可，打包相关的 loader 由插件内置：

```xml
<dependencies>
    <dependency>
        <groupId>cn.jiebaba.summer</groupId>
        <artifactId>summer-boot</artifactId>
        <version>3.0.0</version>
    </dependency>
    <!-- 业务依赖、JDBC 驱动等按需添加 -->
</dependencies>
```

> 数据库驱动：把 JDBC 驱动（如 `postgresql`）声明为 `summer-sample` 的依赖即可，它会被自动收入 `BOOT-INF/lib`，无需在命令行手动拼接。
## 配置

优先加载 `application.yml`，其次 `application.properties`。`application.yml`：

```yaml
server:
  port: 8080
  host: 0.0.0.0

summer:
  datasource:
    url: jdbc:postgresql://host:5432/postgres
    username: postgres
    password: 'secret'
    driver-class-name: org.postgresql.Driver   # 方言由驱动类名自动映射，无需配置 dialect
    pool-size: 4

logging:
  level:
    root: INFO
  file:
    enabled: true
    path: logs
    rolling-policy: time
```

## HTTP keep-alive

`yaml
server:
  keep-alive: true              # 开启连接复用（默认 true）
  keep-alive-timeout: 30000     # keep-alive 空闲超时（毫秒）
  max-requests-per-connection: 100  # 单连接最大请求数
``n
开启后同一 TCP 连接可处理多个请求，减少握手开销。客户端发送 Connection: close 或达到最大请求数后关闭。

环境变量自动映射为松弛属性：`SERVER_PORT` → `server.port`；系统属性优先级最高。`@Value("${app.greeting}")` 可注入并解析 `${key:default}` 占位符。

## 注解速查

### IoC（summer-core）

| 注解 | 作用 |
| --- | --- |
| `@Component` | 通用组件，被扫描注册 |
| `@Service` `@Repository` `@Controller` `@Configuration` | 语义化构型，均元标注 `@Component` |
| `@Bean` | `@Configuration` 类的方法，注册返回值为 bean |
| `@Autowired` | 构造器/字段/setter 注入 |
| `@Qualifier` | 多候选按名消歧 |
| `@Primary` | 多候选优先 |
| `@Value("${key:default}")` | 注入配置值 |
| `@Scope("prototype")` | 原型作用域 |
| `@PostConstruct` `@PreDestroy` | 生命周期回调 |
| `@ComponentScan` | 覆盖扫描根包 |

### Web（summer-web）

| 注解 | 作用 |
| --- | --- |
| `@RestController` | REST 控制器（元标注 `@Controller` + `@ResponseBody`） |
| `@RequestMapping` `@GetMapping` `@PostMapping` `@PutMapping` `@DeleteMapping` `@PatchMapping` | 路径与方法映射，支持 `{var}` 路径变量 |
| `@PathVariable` | 绑定路径变量 |
| `@RequestParam` | 绑定查询参数（支持数组/集合/默认值） |
| `@RequestBody` | 绑定请求体（JSON 反序列化） |
| `@RequestHeader` | 绑定请求头 |
| `@Valid` | 触发参数校验（见 [参数校验](validation.md)） |
| `@RestControllerAdvice` `@ExceptionHandler` | 全局异常处理 |
| `@ResponseStatus` | 自定义响应状态码 |

跨域（CORS）通过 `summer.web.cors.*` 配置自动启用，详见 [CORS](cors.md)。

### 校验约束（summer-web）

| 注解 | 说明 |
| --- | --- |
| `@NotNull` `@NotBlank` `@NotEmpty` | 非空校验 |
| `@Min(value)` `@Max(value)` | 数值上下限 |
| `@Size(min, max)` | 字符串/集合长度区间 |
| `@Pattern(regexp)` | 正则匹配 |
| `@Email` | 邮箱格式 |

均支持 `message` 自定义提示。详见 [参数校验](validation.md)。

### AOP（summer-core）

| 注解 | 作用 |
| --- | --- |
| `@Aspect` | 声明切面类 |
| `@Pointcut("execution(...)")` | 可复用切点 |
| `@Around` | 环绕通知（需 `jp.proceed()`） |
| `@Before` `@After` | 前置/后置通知 |
| `@AfterReturning` `@AfterThrowing` | 返回/异常通知 |

详见 [AOP](aop.md)。

### 定时任务（summer-core）

| 注解 | 属性 |
| --- | --- |
| `@Scheduled` | `cron` / `fixedRate` / `fixedDelay` / `initialDelay` |

详见 [定时任务](scheduling.md)。

### WebSocket（summer-web）

| 注解 | 作用 |
| --- | --- |
| `@WebSocketEndpoint("/ws/echo")` | 声明 WebSocket 端点（元标注 `@Component`） |
| `@OnOpen` `@OnMessage` `@OnClose` `@OnError` | 连接生命周期回调 |

详见 [WebSocket](websocket.md)。
### 多数据源（summer-data）

| 注解 | 作用 |
| --- | --- |
| `@DS("name")` | 指定数据源（方法/类级别） |
| `@Master` `@Slave` | `@DS("master")` / `@DS("slave")` 快捷别名 |
| `@DSTransactional` | 多数据源事务（跨源统一提交/回滚） |

详见 [多数据源](multi-datasource.md)。

### 数据访问（summer-data）

| 注解 | 作用 |
| --- | --- |
| `@TableName` `@TableId` `@TableField` `@TableLogic` | 实体映射 |
| `@Transactional` | 声明式事务（`rollbackFor`/`noRollbackFor`/`readOnly`） |

详见 [数据访问 ORM](orm.md)。

## 工具集（summer-core utils）

summer 在 `cn.jiebaba.summer.core.util` 下提供纯 JDK 工具类，API 风格参考 commons-lang3 与 hutool，业务代码可直接静态调用。详见 [工具集](utils.md)。

| 工具类 | 参考 | 说明 |
| --- | --- | --- |
| `StringUtil` | commons-lang3 `StringUtils` | 判空、截取、split/join、填充、替换、大小写、判断（全 `null` 容错） |
| `DateUtil` | hutool `DateUtil` | 格式化/解析、偏移、区间、边界、字段提取（基于 `java.time`） |
| `JsonUtil` | hutool `JSONUtil` | 序列化/解析/类型绑定，含 `JSONObject`/`JSONArray` |
| `SecurityUtil` | hutool `SecureUtil` | 摘要、HMAC、AES/DES/RSA 加解密、签名验签、Base64/Hex、UUID |
| `SummerUtil` | — | IoC 容器静态门面：获取 / 注册 / 注销 Bean |

```java
// StringUtil —— 参考 commons-lang3，全 null 容错
StringUtil.isBlank("   ");                    // true
StringUtil.split("a,b,c", ",");               // ["a","b","c"]
StringUtil.join(List.of("a","b","c"), "-");   // a-b-c
StringUtil.capitalize("hello");               // Hello
StringUtil.leftPad("1", 3, '0');              // 001
StringUtil.substringBeforeLast("a.b.c", "."); // a.b

// DateUtil —— 参考 hutool，底层 java.time
DateUtil.now();                               // 2024-06-15 10:20:30
DateUtil.parseDateTime("2024-06-15 10:20:30");// Date
DateUtil.offsetDay(date, 1);                  // 加一天
DateUtil.betweenDay(begin, end);              // 相差天数
DateUtil.beginOfMonth(date);                  // 月初 00:00:00
DateUtil.isWeekend(date);                     // 是否周末

// JsonUtil —— 参考 hutool JSONUtil，纯 JDK
JsonUtil.toJsonStr(obj);                      // 紧凑 JSON
JsonUtil.toJsonPrettyStr(obj);                // 带缩进
JsonUtil.parseObj("{\"a\":1}");               // JSONObject
JsonUtil.toBean(json, User.class);            // 反序列化为对象
JsonUtil.toList(json, User.class);            // 反序列化为 List

// SecurityUtil —— 参考 hutool SecureUtil，java.security/javax.crypto
SecurityUtil.md5Hex("abc");                   // 900150983cd24fb0d6963f7d28e17f72
String enc = SecurityUtil.encryptAES("明文", "key");
SecurityUtil.decryptAES(enc, "key");          // 明文
KeyPair kp = SecurityUtil.generateRSAKeyPair();
SecurityUtil.signBase64(kp.getPrivate(), "msg");

// SummerUtil —— IoC 容器静态门面（SummerApplication.run() 自动绑定上下文）
SummerUtil.getBean(MyService.class);
SummerUtil.containsBean("myService");
SummerUtil.registerBean("custom", new MyService());  // 注册单例
SummerUtil.unregisterBean("custom");                 // 注销（触发 @PreDestroy / DisposableBean）
SummerUtil.unregisterBean(MyService.class);          // 按类型批量注销
```

> `registerBean` 在同名 Bean 已存在时抛 `BeansException`，需先 `unregisterBean` 再注册以替换。
## 示例（summer-sample）

```java
@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
    @ResponseStatus(201)
    public Product create(@Valid @RequestBody Product product) {
        productService.save(product);
        return product;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping
    public List<Product> list(@RequestParam(value = "name", required = false) String name,
                              @RequestParam(value = "page", defaultValue = "1") int page,
                              @RequestParam(value = "size", defaultValue = "10") int size) {
        if (name != null && !name.isEmpty()) {
            return productService.searchByName(name, page, size).records();
        }
        return productService.list();
    }
}
```

## JSON 字段（JSONB）

summer-data 的 `TypeHandler` + 方言驱动 JSON 类型绑定，一个 `@TableField(typeHandler=...)` 声明即可让读写双向按当前方言出 `jsonb`/`json`/`CLOB`。设计细节见 [开发文档 - TypeHandler 与方言驱动的 JSON 类型](../开发文档/architecture.md)。

**建表**（PostgreSQL）：

```sql
CREATE TABLE summer_widget (
    id     BIGINT PRIMARY KEY,
    name   VARCHAR(128) NOT NULL,
    attrs  JSONB NOT NULL
);
```

**实体**：

```java
@TableName("summer_widget")
public class Widget {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @NotBlank
    private String name;
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> attrs;
    // 构造器 / getter / setter 省略
}
```

**读写**：写入时 `Map` 经 `JsonUtil` 序列化成 JSON 文本，再由方言包成 `PGobject(type="jsonb")` 绑定；读取时反向取回文本并反序列化成 `Map`，对调用方完全透明。

```java
widgetService.save(widget);                      // attrs → JSONB 列
Widget loaded = widgetService.getById(widget.getId());
loaded.getAttrs().get("color");                 // 自动反序列化回 Map
```

**方言适配**：同一实体在 MySQL 上写 `json` 列（`setString`）、Oracle 写 `CLOB`、SQL Server 写 `nvarchar(max)`，无需改代码。方言由 JDBC 驱动类名自动映射（`org.postgresql.Driver`→PostgreSQL 等），无需配置 `dialect`。

## 测试结果

| 测试 | 断言 | 覆盖 |
| --- | --- | --- |
| `SmokeTest` | 11 路由 | Web 全链路（路由/绑定/JSON/异常/`@Value`） |
| `OrmSmokeTest` | 28 项 | ORM 纯逻辑（元数据/SQL/Wrapper/Lambda/分页） |
| `DbSmokeTest` | 16 项 | 真实 PostgreSQL：CRUD/分页/事务提交回滚/校验 |
| `JsonTypeHandlerTest` | 10 项 | TypeHandler + 方言驱动 JSON：`JdbcValue` 包装、PG `PGobject(jsonb)`、MySQL `setString`、读路径反序列化、`fromDriver` 驱动映射 |

校验失败示例：`POST /products {"name":null,"price":-5}` → `400` + `{"violations":["name: name must not be blank","price: price must be non-negative"]}`。

## 异步控制器

控制器方法可返回 `CompletableFuture` / `CompletionStage`，框架在虚拟线程上 `join()` 等待结果后序列化（虚拟线程下阻塞不占平台线程）：

```java
@GetMapping("/async")
public CompletableFuture<Map<String, Object>> async() {
    return CompletableFuture.supplyAsync(() -> {
        // 并行 IO 聚合场景
        return Map.of("async", true);
    });
}
```

> 在虚拟线程模型下，同步阻塞已不占平台线程，异步的主要价值是并行聚合多个独立 IO（`CompletableFuture.allOf`）。
## 日志

框架自身用 `java.util.logging`。应用日志建议同样用 JUL（零依赖），配置见 [日志方案](logging.md)。
