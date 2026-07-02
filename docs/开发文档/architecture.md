﻿# 架构设计

## 模块划分

```
summer-parent (pom)
├── summer-core            IoC 容器 / 依赖注入 / 组件扫描 / 配置环境 / 日志 / AOP / 定时任务 / 工具集（utils） / 自研测试微框架（core.test 包）
├── summer-web             嵌入式 HTTP 服务器（ServerSocket+虚拟线程）/ 路由 / JSON / 参数绑定 / 异常 / 校验
├── summer-data            ORM：BaseMapper/Wrapper/分页/IService/事务/多方言，纯 JDBC，零第三方依赖
├── summer-boot            SummerApplication.run() 启动器 / 自动配置 / 数据源 / Mapper装配 / 关闭钩子
├── summer-boot-loader     可执行 jar 启动器 JarLauncher（java -jar 入口，BOOT-INF 解压+类路径重建），由 summer-pack-maven-plugin 内置打包
├── summer-pack-maven-plugin  repackage goal：mvn package 自动产出 BOOT-INF 可执行 jar
├── summer-sample          示例应用（Application + controller/service/repository/aspect），端到端验证
└── build-test              集中式测试：AOP 单测/集成测试 + sample 冒烟测试（依赖 summer-sample）
```

## 依赖关系（自底向上）

```
summer-sample ──depends──> summer-boot ──depends──> summer-data ──depends──> summer-core
                                └──depends──> summer-web  ──depends──> summer-core
summer-pack-maven-plugin ──depends──> summer-boot-loader  （可执行 jar 启动器，插件内置）
build-test ──depends──> summer-sample / summer-boot / summer-data / summer-core  （集中式测试）
```

- `summer-core` 是地基，零第三方依赖；
- `summer-web` 依赖 `summer-core`（不再依赖 `jdk.httpserver`，改用 `java.net`）；
- `summer-data` 依赖 `summer-core`（用 JDK 的 `java.sql`）；
- `summer-boot` 组装 core + web + data，提供启动入口；`summer-boot-loader` 提供可执行 jar 启动器，作为 `summer-pack-maven-plugin` 的依赖被内置打包，应用项目无需单独声明；
- `summer-sample` 是使用者，仅需依赖 boot，打包由 `summer-pack-maven-plugin` 内置 loader，业务包无需额外声明（classpath 模式，反射不受强封装限制）。

## summer-core 职责

| 包 | 内容 |
| --- | --- |
| `cn.jiebaba.summer.core.annotation` | 构造型与 DI 注解：`@Component/@Service/@Repository/@Controller/@Configuration`、`@Bean/@Autowired/@Value/@Scope/@Qualifier/@Primary/@Lazy/@PostConstruct/@PreDestroy/@ComponentScan/@Order` |
| `cn.jiebaba.summer.core.context` | `ApplicationContext` 接口、`DefaultApplicationContext`（IoC 容器实现）、`BeanDefinition`、生命周期接口 |
| `cn.jiebaba.summer.core.env` | `Environment`：属性加载、`${key:default}` 占位符解析、类型转换；`YamlParser` 解析 `application.yml` |
| `cn.jiebaba.summer.core.scanner` | `ClassPathScanner`（类路径类扫描）、`AnnotationUtils`（元注解递归查找） |
| `cn.jiebaba.summer.core.aop` | `@Aspect/@Pointcut/@Around/@Before/@After/@AfterReturning/@AfterThrowing`、`PointcutMatcher`（`execution()` 表达式）、`AdvisedProxyFactory`（JDK 动态代理 + 拦截器链）、`SubclassProxyFactory`（手写字节码子类代理，无接口 bean 走此路径）、`SummerProxy`（子类代理标记）、`JoinPoint/ProceedingJoinPoint` |
| `cn.jiebaba.summer.core.scheduling` | `@Scheduled`（cron/fixedRate/fixedDelay）、`CronExpression`（5 段表达式 + 下次触发计算）、`ScheduledTaskRegistrar`（定时线程池触发 + 虚拟线程执行任务体） |
| `cn.jiebaba.summer.core.logging` | `LoggingInitializer`、`DailyRollingFileHandler`（按天/按天+大小滚动 + 历史清理）、`SingleLineFormatter`、`LogProperties` |
| `cn.jiebaba.summer.core.util` | `ReflectionUtils`；工具集：`StringUtil`（参考 commons-lang3）、`DateUtil`（参考 hutool，java.time）、`JsonUtil`（参考 hutool JSONUtil）、`SecurityUtil`（加解密/摘要/HMAC/签名）、`SummerUtil`（IoC 容器静态门面，获取/注册/注销 Bean） |

### IoC 关键机制

- **构造器注入**：优先无参构造，有 `@Autowired` 构造器则选它；
- **字段/Setter 注入**：`@Autowired` 字段与 setter 方法在 `populateBean` 阶段注入；
- **`@Value`**：从 `Environment` 解析占位符并按类型转换；
- **循环依赖**：单例三级缓存（`singletonObjects` / `earlySingletonObjects` / `inCreation`），构造器循环抛异常；
- **集合/数组注入**：把同类型所有 bean 注入为 `List`/数组；
- **`@Qualifier/@Primary`**：多候选消歧；
- **生命周期**：`@PostConstruct` → `InitializingBean` → `initMethod`；销毁逆序 `@PreDestroy` → `DisposableBean` → `destroyMethod`；
- **AOP 集成**：`preInstantiateSingletons` 先实例化 `@Aspect` 并收集 advisor；单例创建时按需代理——有接口走 JDK 动态代理（`AdvisedProxyFactory`），无接口且非 final 走手写字节码子类代理（`SubclassProxyFactory`，桥接方法 `$$summer$super$` 破自调用递归）；`@Transactional` 的 `TransactionInterceptor` 同属拦截器链。

## summer-web 职责

| 包 | 内容 |
| --- | --- |
| `cn.jiebaba.summer.web.annotation` | `@RestController/@RequestMapping/@GetMapping...`、`@PathVariable/@RequestParam/@RequestBody/@RequestHeader`、`@RestControllerAdvice/@ExceptionHandler/@ResponseStatus` |
| `cn.jiebaba.summer.web.http` | `HttpMethod/MediaType/HttpStatus`、`RawHttpRequest`（HTTP/1.1 解析）、`WebRequest/WebResponse` |
| `cn.jiebaba.summer.web.json` | `Json`：手写序列化器 + 递归下降解析器 + 对象绑定 |
| `cn.jiebaba.summer.web.routing` | `RoutePattern`（路径变量/通配符匹配 + 特异性评分）、`Router` |
| `cn.jiebaba.summer.web.convert` | `MessageConverter` / `JsonMessageConverter` |
| `cn.jiebaba.summer.web.bind` | `HandlerMethodInvoker`（参数绑定，含 `@Valid` 校验触发） |
| `cn.jiebaba.summer.web.support` | `WebRouteRegistrar`（控制器→路由）、`ExceptionHandlerRegistry` |
| `cn.jiebaba.summer.web.validation` | `@Valid` + `@NotNull/@NotBlank/@NotEmpty/@Min/@Max/@Size/@Pattern/@Email`、`Validator`（递归嵌套）、`ValidationException`、`ConstraintViolation` |
| `cn.jiebaba.summer.web.server` | `SummerWebServer`（ServerSocket + 虚拟线程执行器）、`RequestDispatcher`、`WebServerProperties` |

### 请求处理流程

```
Socket.accept()  (虚拟线程)
   │
   ▼
RawHttpRequest.parse(InputStream)
   │
   ├─ WebSocket Upgrade? → 101 握手 → WebSocketSession.runLoop()（帧循环）
   │
   ▼
WebRequest
   │
   ▼
RequestDispatcher.dispatch
   │  去掉 contextPath → Router.match(method, path)
   ▼
RouteMatch（mapping + pathVariables）
   │
   ▼
HandlerMethodInvoker.invoke
   │  绑定 @PathVariable/@RequestParam/@RequestBody/@RequestHeader
   │  @Valid 参数 → Validator 校验 → 失败抛 ValidationException → 400
   ▼
控制器方法执行 → 返回值（经 AOP 代理）
   │
   ▼
writeResult：@ResponseBody → JSON；String → text；byte[] → octet-stream
   │  异常 → ExceptionHandlerRegistry → @ExceptionHandler 或默认错误体
   ▼
WebResponse.commit（写状态行+头+body 到 socket）
```

### 路由匹配

- `RoutePattern` 把 `/users/{id}/repos/{name}` 拆段，字面量段精确匹配、`{var}` 段捕获、`*` 通配、`/**` 尾部全捕获；
- 注册后按 **特异性评分排序**（字面量 > 变量，长 > 短），先到先匹配，保证精确路由优先于通配；
- 类级 `@RequestMapping("/users")` + 方法级 `@GetMapping("/{id}")` 合并为 `/users/{id}`。

## summer-data 职责

| 包 | 内容 |
| --- | --- |
| `cn.jiebaba.summer.data.annotation` | `@TableName/@TableId/@TableField/@TableLogic`、`IdType`；`@TableField(typeHandler=...)` 绑定自定义类型处理器 |
| `cn.jiebaba.summer.data.metadata` | `MetadataParser`、`TableInfo/TableFieldInfo`、`NamingUtils`（驼峰转下划线） |
| `cn.jiebaba.summer.data.conditions` | `AbstractWrapper`、`QueryWrapper`（字符串列）、`LambdaQueryWrapper`（方法引用列）、`LambdaUtils`、`SFunction` |
| `cn.jiebaba.summer.data.mapper` | `BaseMapper`、`MapperProxyFactory`（JDK 动态代理）、`MapperSupport`（SQL 执行胶水） |
| `cn.jiebaba.summer.data.page` | `Page`、`IPage` |
| `cn.jiebaba.summer.data.service` | `IService`、`ServiceImpl` |
| `cn.jiebaba.summer.data.dialect` | `Dialect` 接口 + 四方言；`fromDriver(driver)` 按驱动类名映射、`detect(driver,url)` 驱动优先空则按 URL 推断、`of(name)` 通用按名取；含 `jsonColumnType()`/`setJsonParameter()`/`getJsonResult()` 方言级 JSON 类型绑定 |
| `cn.jiebaba.summer.data.transaction` | `@Transactional`、`TransactionManager`（ThreadLocal 连接栈）、`TransactionInterceptor`（AOP 织入） |
| `cn.jiebaba.summer.data.support` | `SqlBuilder`（持方言字段）、`SqlExecutor`（事务感知，接 TypeHandler）、`DataSourceFactory`（轻量连接池）、`DataProperties`、`IdGenerator`、`DataAccessException`、`TypeHandler`/`JdbcValue`/`JsonTypeHandler` |

### TypeHandler 与方言驱动的 JSON 类型

MyBatis 的 `TypeHandler<T>` 负责逐参数的 Java↔JDBC 绑定（`ParameterMapping` 带 `javaType/jdbcType/typeHandler`），summer-data 对齐此设计但简化为两层职责切分：

- **`TypeHandler`**（`summer-data.support`）：`setParameter()`/`getResult()`，负责 **Java 对象 ↔ JSON 文本**（序列化用 summer-core `JsonUtil`，零第三方依赖）。
- **`Dialect`**：`jsonColumnType()`/`setJsonParameter()`/`getJsonResult()` 负责 **JSON 文本 ↔ 原生列类型**，按方言实现：
  - PostgreSQL → `jsonb`，用 `PGobject(type="jsonb")`（反射构建并缓存，不硬依赖驱动类，summer-data 编译期无需 pg 驱动）；
  - MySQL → `json`，`setString` 即可；Oracle → `CLOB`，`setString`；SQL Server → `nvarchar(max)`，`setString`。

内置 `JsonTypeHandler` 一个声明即可让读写双向按当前方言出 `jsonb`/`json`/`CLOB`：

```java
@TableField(typeHandler = JsonTypeHandler.class)
private Map<String, Object> config;
```

- **写路径**：`SqlBuilder` 把带 handler 的字段值包成 `JdbcValue(value, handler)`（仅持引用，不碰 JDBC，保持可单测），`SqlExecutor.bind()` 遇 `JdbcValue` 调 `handler.setParameter(ps, i, value, dialect)`，否则原 `setObject`。
- **读路径**：`SqlExecutor.mapRows()` 字段有 handler 时调 `handler.getResult(rs, i, javaType, dialect)`，否则原 `getObject()` + `coerce()`。无需 MyBatis-Plus 的 `autoResultMap`——直接反射赋值，handler 自动对读/写生效。

> 边界：`WHERE` 条件值（`AbstractWrapper.params`）仍为裸 `Object`，JSON 列条件查询暂不套 handler（与 MyBatis-Plus 同理，需后续增强）；多数据源按数据源各自解析方言（per-DS dialect）为第二阶段，当前为「单 dialect + URL 推断」。

## summer-boot 职责

- `SummerApplication.run(Class<?> primarySource, String[] args)`：
  1. 推断主类所在包为扫描根包（支持 `@SummerApplication(scanBasePackages=...)` / `@ComponentScan` 覆盖）；
  2. 构建 `Environment`（加载 `application.yml`/`.properties`）、`LoggingInitializer.initialize`；
  3. 注册自动配置类（`DataAutoConfiguration`）+ `MapperRegistrar.registerDefinitions`（mapper bean 定义）；
  4. `context.refresh()` 完成扫描与 Bean 装配（含 AOP 代理织入）；
  5. `WebRouteRegistrar.build(context)` 构建路由与异常注册表；
  6. `SummerWebServer.createDefault(...).start()`；
  7. `ScheduledTaskRegistrar.scheduleAll(context)` 调度定时任务；
  8. 注册 JVM 关闭钩子：停定时任务 + 停服务器 + `context.close()`。
- `DataAutoConfiguration`（`@Configuration`）：配置了 `summer.datasource.url` 时创建 `DataSource/SqlExecutor/Dialect` + 事务组件，并注册 Mapper 代理；`Dialect` 由 `Dialect.detect(driver, url)` 自动映射（按 JDBC 驱动类名，如 `org.postgresql.Driver`→PostgreSQL；驱动为空则按 URL 推断），无需配置 `dialect`，`SqlExecutor` 注入该方言供读写时透传 TypeHandler。

## 运行时模型

- 启动：`java -jar summer-sample\target\summer-sample-3.0.0-boot.jar`
- 每个连接一个虚拟线程，阻塞 IO 不占平台线程；
- 定时任务体在虚拟线程上执行；
- 单进程、单 JVM，无外部容器。
