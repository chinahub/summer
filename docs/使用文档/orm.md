# 数据访问 ORM（summer-data）

模仿 MyBatis-Plus 的使用方式，底层用纯 JDBC（`java.sql`，JDK 内置），不引入 MyBatis。框架核心零第三方依赖；JDBC 驱动由使用者自备（运行时依赖）。

## 模块

`summer-data`（依赖 `summer-core`，用 JDK 的 `java.sql`），由 `summer-boot` 自动配置装配。

## 实体注解

| 注解 | 作用 |
| --- | --- |
| `@TableName("product")` | 表名（默认驼峰转下划线） |
| `@TableId(type = IdType.ASSIGN_ID)` | 主键，`IdType`：`AUTO`/`INPUT`/`ASSIGN_ID`/`ASSIGN_UUID` |
| `@TableField("stock_qty")` | 字段映射；`exist=false` 排除非数据库字段；`typeHandler=JsonTypeHandler.class` 绑定 JSONB 列（见下文） |
| `@TableLogic` | 逻辑删除字段（删除变 UPDATE，查询自动过滤） |

```java
@TableName("product")
public class Product implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @NotBlank(message = "name must not be blank")
    private String name;
    @Min(value = 0, message = "price must be non-negative")
    private Integer price;
    @TableField("stock_qty")
    private Integer stock;
    @TableField(exist = false)
    private String transientFlag;
    // getters/setters...
}
```

> 实体需 `implements Serializable`（Lambda 条件构造器解析方法引用需要）。

## BaseMapper

```java
public interface ProductMapper extends BaseMapper<Product> {}
```

框架扫描 `BaseMapper` 子接口，用 JDK 动态代理生成实现，注册为 bean，可直接 `@Autowired`。内置方法：

| 方法 | 说明 |
| --- | --- |
| `insert(T)` | 插入（ASSIGN_ID/UUID 自动生成主键，AUTO 回填） |
| `deleteById(id)` | 删除（有 `@TableLogic` 则逻辑删除） |
| `updateById(T)` | 按主键更新（非空字段） |
| `selectById(id)` | 按主键查询 |
| `selectList()` / `selectList(wrapper)` | 列表查询 |
| `selectOne(wrapper)` | 单条查询（多条抛异常） |
| `selectCount(wrapper)` | 计数 |
| `selectPage(page, wrapper)` | 分页 |

## SQL 日志

打开 `SqlExecutor` 的 DEBUG 日志即可打印 SQL，格式参考 MyBatis：

```yaml
logging:
  level:
    cn.jiebaba.summer.data.support.SqlExecutor: DEBUG
```

示例输出：

```text
==> Preparing: SELECT id, name FROM product WHERE id = ?
==> Parameters: 1(Long)
<== Total: 1 (3 ms)
```

## 条件构造器 Wrapper

### QueryWrapper（字符串列）

```java
new QueryWrapper<Product>()
    .eq("price", 100).like("name", "phone").ge("stock_qty", 10)
    .orderByDesc("price").last("LIMIT 5");
// → WHERE price = ? AND name LIKE ? AND stock_qty >= ? ORDER BY price DESC LIMIT 5
```

### LambdaQueryWrapper（方法引用列）

```java
new LambdaQueryWrapper<Product>()
    .eq(Product::getName, "phone").gt(Product::getPrice, 100)
    .orderByDesc(Product::getPrice);
// → WHERE name = ? AND price > ? ORDER BY price DESC
```

支持：`eq/ne/gt/ge/lt/le`、`like/likeLeft/likeRight/notLike`、`isNull/isNotNull`、`in/notIn`、`between`、`orderByAsc/Desc`、`groupBy`、`and(Consumer)/or(Consumer)` 嵌套、`last`。采用自类型泛型，链式调用保留具体类型。

## 分页

```java
Page<Product> page = new Page<>(2, 20);   // current, size
IPage<Product> result = mapper.selectPage(page, wrapper);
// PostgreSql: ... LIMIT ? OFFSET ?   (offset = (current-1)*size)
```

## IService / ServiceImpl

```java
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
    @Autowired
    public void setMapper(ProductMapper mapper) { this.baseMapper = mapper; }

    public Page<Product> searchByName(String keyword, int current, int size) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .like(Product::getName, keyword).orderByDesc(Product::getPrice);
        return (Page<Product>) page(new Page<>(current, size), w);
    }
}
// 可用 save/saveBatch/updateById/removeById/getById/list/getOne/count/page
```

## 事务管理 @Transactional

基于 ThreadLocal 连接栈的声明式事务，与 AOP 拦截器链集成。

```java
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> {

    @Transactional(rollbackFor = Exception.class)
    public void batchInsert(String name1, String name2, boolean fail) {
        baseMapper.insert(new Product(name1, 100, 1));
        baseMapper.insert(new Product(name2, 100, 1));
        if (fail) {
            throw new RuntimeException("simulated failure -> rollback both inserts");
        }
    }
}
```

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `rollbackFor` | `{}`（空） | 默认对 `RuntimeException`/`Error` 回滚；指定则按指定类型回滚 |
| `noRollbackFor` | `{}` | 指定不回滚的异常类型 |
| `readOnly` | `false` | 标记只读事务 |
| `value` | `""` | 事务名（预留多事务管理器） |

- **嵌套事务**：`TransactionManager` 用 ThreadLocal `Deque<Connection>` 栈，内层事务**加入**外层（同连接），整体提交/回滚；
- 事务执行期间 `SqlExecutor` 复用当前线程的连接，**不**在每条语句后关闭，由事务边界统一提交/回滚后归还连接池；
- `@Transactional` 可标注在方法或类上（类级对该类所有方法生效）；
- 由 `TransactionInterceptor`（实现 `MethodInterceptor` + `ProxyAdvisor`）解析注解并织入，注册于 `DataAutoConfiguration`。

实测：`batchInsert(..., true)` 抛异常后两条 insert 全部回滚，计数不变；`batchInsert(..., false)` 正常提交，记录数 +2。

## SQL 方言（自动识别）

`Dialect` 抽象分页 SQL 与方言级类型绑定（如 JSON 列）。**无需配置**——由 `Dialect.detect(driver, url)` 在启动时自动映射：按 JDBC 驱动类名识别，驱动为空时回退按 URL 推断。

| 驱动类名（包含） | 方言 | 分页方式 | JSON 列类型 |
| --- | --- | --- | --- |
| `postgresql` | PostgreSqlDialect | `LIMIT ? OFFSET ?` | `jsonb`（`PGobject`） |
| `mysql` / `mariadb` / `h2` | MySqlDialect | `LIMIT ? OFFSET ?` | `json`（`setString`） |
| `oracle` | OracleDialect | `OFFSET ? FETCH NEXT ? ROWS ONLY` | `CLOB`（`setString`） |
| `sqlserver` | SqlServerDialect | `OFFSET ? FETCH NEXT ? ROWS ONLY` | `nvarchar(max)`（`setString`） |
| 未识别 | PostgreSqlDialect | `LIMIT ? OFFSET ?` | 默认 |

`Dialect.appendPagination(sql, offset, size, params)` 按各方言正确处理参数顺序；`jsonColumnType()`/`setJsonParameter()`/`getJsonResult()` 让 JSON 字段按方言绑定原生列类型（见下文 TypeHandler）。`SqlBuilder` 持有 dialect 字段，`DataAutoConfiguration` 通过 `Dialect.detect(driver, url)` 注册 `Dialect` bean。

### TypeHandler 与 JSONB 列

对标 MyBatis 的 `TypeHandler<T>`，summer-data 拆为两层职责：

- **`TypeHandler`**（`setParameter()`/`getResult()`）：负责 Java 对象 ↔ JSON 文本，序列化用 summer-core `JsonUtil`（零第三方依赖）。
- **`Dialect`**：负责 JSON 文本 ↔ 原生列类型，按方言实现（PG 用 `PGobject(type="jsonb")`，反射构建不硬依赖驱动类）。

内置 `JsonTypeHandler`，一个声明即可读写双向按当前方言出 `jsonb`/`json`/`CLOB`：

```java
@TableName("summer_widget")
public class Widget {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> attrs;   // 读写自动序列化/反序列化
    // getters/setters...
}
```

- **写路径**：`SqlBuilder` 把带 handler 的字段值包成 `JdbcValue(value, handler)`（仅持引用，保持可单测），`SqlExecutor.bind()` 遇 `JdbcValue` 调 `handler.setParameter()`，否则原 `setObject`。
- **读路径**：`mapRows()` 字段有 handler 时调 `handler.getResult()`，否则原 `getObject()` + `coerce()`。无需 MyBatis-Plus 的 `autoResultMap`——直接反射赋值，handler 自动对读/写生效。

> 边界：`WHERE` 条件值（`AbstractWrapper.params`）暂不套 handler，JSON 列条件查询需后续增强；多数据源按数据源各自解析方言（per-DS dialect）为第二阶段，当前为「单 dialect + 自动识别」。

## 数据源配置

`application.yml`（推荐）或 `application.properties`：

```yaml
summer:
  datasource:
    url: jdbc:postgresql://host:5432/postgres
    username: postgres
    password: 'secret'
    driver-class-name: org.postgresql.Driver   # 方言由驱动类名自动映射，无需配置 dialect
    pool-size: 4                      # 最大连接数（上限，按需懒创建至此上限）
    minimum-idle: 4                   # 最小空闲数；后台保活维持此下限（默认=pool-size，始终保持满）
    connection-timeout: 30000         # 借出等待超时（毫秒），池满时阻塞至此超时
    idle-timeout: 600000              # 空闲超过此值且数量>minimum-idle 时回收（毫秒）
    max-lifetime: 1800000             # 连接最大存活（毫秒），每条连接随机抖动 ±2.5% 避免同时过期
    keepalive-time: 0                 # 空闲探活间隔（毫秒），0=关闭；代理后建议开启
    keepalive-query: SELECT 1         # 探活 SQL
    leak-detection-threshold: 60000   # 泄漏检测阈值（毫秒），0=关闭
```

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `pool-size` | 8 | 连接池上限（最大连接数），按需懒创建至此上限 |
| `minimum-idle` | =`pool-size` | 最小空闲连接数；后台线程维持此下限，超出的空闲连接由 `idle-timeout` 回收 |
| `connection-timeout` | 30000 | 借出等待超时（毫秒），超时抛 `SQLException` |
| `idle-timeout` | 600000 | 空闲连接存活时长（毫秒），仅当空闲数 > `minimum-idle` 时回收 |
| `max-lifetime` | 1800000 | 连接最大存活时长（毫秒），每条连接随机抖动 ±2.5% 避免同时过期；过期后自动替补 |
| `keepalive-time` | 0（关闭） | 空闲超过此值时用 `keepalive-query` 探活（毫秒）；置于 PgBouncer/Supabase 等代理后建议开启 |
| `keepalive-query` | `SELECT 1` | 探活 SQL |
| `leak-detection-threshold` | 0（关闭） | 连接持有超过此阈值时打 WARN 日志（含借出调用栈） |

- 内置轻量连接池（HikariCP 风格：`BlockingQueue` + 动态代理 `Connection`，`close()` 归还），虚拟线程友好；
- 池上限 `pool-size`、下限 `minimum-idle`：按需懒创建至上限、空闲回收至下限；后台线程维持下限，**池被回收后能自愈补建**，不会抽干到零需重启；
- `max-lifetime` 每条连接随机抖动 ±2.5%，避免同时刻集体过期；过期空闲连接关闭后立即补建，借出连接归还时软淘汰；
- 借出时校验 `isValid(2)`（刚归还的连接跳过校验），失效连接自动重建；
- `keepalive-time` 可对长空闲连接定时探活，防止被 PgBouncer/Supabase 等代理回收；
- 泄漏检测：后台守护虚拟线程定期扫描未归还连接，超阈值打 WARN（含借出栈），帮助定位忘记 `close()` 的连接；
- 驱动需在模块/类路径上（如 `postgresql`、`mysql-connector-j`），由使用者添加依赖。
## AOP 代理要求（重要）

summer 同时支持两种代理策略（零第三方依赖，不引入 CGLIB 第三方库——子类代理为手写字节码的自研实现）。`@Transactional` 与 `@Aspect` 切面通过代理织入，代理策略自动判断：

- **有接口** → JDK 动态代理（`AdvisedProxyFactory`）；
- **无接口且非 `final`**（且通过构造器实例化）→ 手写字节码子类代理（`SubclassProxyFactory`，CGLIB 风格，零依赖），事务/AOP 同样生效；
- **`final` 类**或**工厂方法 / `instanceSupplier`** 产生的无接口 bean 无法子类代理，仍抛 `BeansException`（而非静默失效）；


```java
// ✅ 方式一：Service 实现接口，走 JDK 动态代理，@Transactional 生效
public interface OrderService { void placeOrder(Order order); }
@Service
public class OrderServiceImpl implements OrderService {
    @Transactional
    public void placeOrder(Order order) { ... }
}

// ✅ 方式二：无接口但非 final，走子类代理（CGLIB 风格），@Transactional 同样生效
@Service
public class OrderService {
    @Transactional
    public void placeOrder(Order order) { ... }
}

// ❌ 错误：final 类无法子类代理，启动时报 BeansException
@Service
public final class FinalOrderService {
    @Transactional
    public void placeOrder(Order order) { ... }
}
```

> 控制器（`@RestController`）不经过代理，不受此限制。子类代理仅拦截 public/protected 非 final 方法（private/static/final 方法不拦截）；方法内自调用也会被拦截（桥接方法 `$$summer$super$` 破递归）。
## AOP 代理要求（重要）

summer 仅使用 JDK 动态代理（零第三方依赖，不引入 CGLIB）。`@Transactional` 和 `@Aspect` 切面通过代理织入，因此：

- 代理策略自动判断：**有接口**走 JDK 动态代理；**无接口且非 `final`**走手写字节码子类代理（`SubclassProxyFactory`，零依赖），事务/AOP 生效；`final` 类或工厂方法产生的无接口 bean 仍抛 `BeansException`（而非静默失效）；


```java
// ✅ 正确：Service 实现接口，@Transactional 生效
public interface OrderService { void placeOrder(Order order); }
@Service
public class OrderServiceImpl implements OrderService {
    @Transactional
    public void placeOrder(Order order) { ... }
}

// ❌ 错误：无接口，启动时报 BeansException
@Service
public class OrderService {
    @Transactional
    public void placeOrder(Order order) { ... }
}
```

> 控制器（`@RestController`）不经过代理，不受此限制。

## 自动配置

`summer-boot` 的 `DataAutoConfiguration`（`@Configuration`）在配置了 `summer.datasource.url` 时：
1. 创建 `DataSource` + `SqlExecutor` + `Dialect` bean（方言由 `Dialect.detect(driver, url)` 自动映射，无需配置）；
2. 注册 `TransactionManager` + `TransactionInterceptor`；
3. `MapperRegistrar` 扫描 `BaseMapper` 子接口，注册代理 bean。

未配置数据源时自动跳过，不影响 Web 应用启动。

## 验证

`DbSmokeTest`（连接真实 PostgreSQL，**16 项断言全过**）：
- DDL 建表 + CRUD（insert/selectById/update/list/count）
- LambdaQueryWrapper（ge + orderByDesc）
- 分页（postgresql 方言 `LIMIT ? OFFSET ?`）
- `@Transactional` 提交（+2 行）与回滚（异常后计数不变）
- 参数校验（2 条违规：name 为空 + price 为负）

`OrmSmokeTest`（纯逻辑，无 DB，**28 项断言全过**）：实体元数据、SQL 生成、Wrapper、Lambda 解析、分页 SQL。

`JsonTypeHandlerTest`（纯逻辑，无 DB，**10 项断言全过**）：TypeHandler + 方言驱动 JSON——`JdbcValue` 包装、PG `PGobject(jsonb)`、MySQL `setString`、读路径反序列化、`fromDriver` 驱动映射、`detect` 优先级。
