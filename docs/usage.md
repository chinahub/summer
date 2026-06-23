# 使用

## 构建

```powershell
$env:JAVA_HOME='D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;D:\mvnd-1.0.5\mvn\bin;" + $env:Path
mvn -s E:\summer_workspace\settings.xml -o clean package
```

产出各模块 `target/*.jar`（JPMS 模块 jar）。

## 运行（模块化方式）

```powershell
java -p summer-core/target/summer-core-1.0.0-SNAPSHOT.jar `
        ;summer-web/target/summer-web-1.0.0-SNAPSHOT.jar `
        ;summer-data/target/summer-data-1.0.0-SNAPSHOT.jar `
        ;summer-boot/target/summer-boot-1.0.0-SNAPSHOT.jar `
        ;summer-sample/target/summer-sample-1.0.0-SNAPSHOT.jar `
     -m summer.sample/cn.jiebaba.summer.sample.Application
```

- `-p` 指定模块路径（Windows 用 `;` 分隔）；
- `-m summer.sample/...` 指定主模块与主类；
- 默认监听 `0.0.0.0:8080`，可用 `server.port` 等覆盖。

> 注意：业务模块需 `opens` 业务包给 `summer.core, summer.web, summer.data`（见 [安装](installation.md)）。若用到数据库，需把 JDBC 驱动 jar 加到模块路径或类路径上。

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
    driver-class-name: org.postgresql.Driver
    pool-size: 4
    dialect: postgresql

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

## 测试结果

| 测试 | 断言 | 覆盖 |
| --- | --- | --- |
| `SmokeTest` | 11 路由 | Web 全链路（路由/绑定/JSON/异常/`@Value`） |
| `OrmSmokeTest` | 28 项 | ORM 纯逻辑（元数据/SQL/Wrapper/Lambda/分页） |
| `DbSmokeTest` | 16 项 | 真实 PostgreSQL：CRUD/分页/事务提交回滚/校验 |

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