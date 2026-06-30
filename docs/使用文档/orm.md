# 数据访问 ORM（summer-data）

模仿 MyBatis-Plus 的使用方式，底层用纯 JDBC（`java.sql`，JDK 内置），不引入 MyBatis。框架核心零第三方依赖；JDBC 驱动由使用者自备（运行时依赖）。

## 模块

`summer-data`（依赖 `summer-core`，用 JDK 的 `java.sql`），由 `summer-boot` 自动配置装配。

## 实体注解

| 注解 | 作用 |
| --- | --- |
| `@TableName("product")` | 表名（默认驼峰转下划线） |
| `@TableId(type = IdType.ASSIGN_ID)` | 主键，`IdType`：`AUTO`/`INPUT`/`ASSIGN_ID`/`ASSIGN_UUID` |
| `@TableField("stock_qty")` | 字段映射；`exist=false` 排除非数据库字段 |
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

## SQL 方言（多方言分页）

`dialect.cn.jiebaba.summer.data.Dialect` 抽象分页 SQL 生成，`Dialect.of(name)` 按名解析：

| 配置值 | 方言 | 分页方式 |
| --- | --- | --- |
| `mysql` / `mariadb` / `h2` | MySqlDialect | `LIMIT ? OFFSET ?` |
| `postgres` / `postgresql` / `pg` | PostgreSqlDialect | `LIMIT ? OFFSET ?` |
| `oracle` | OracleDialect | `ROWNUM` 子查询 |
| `sqlserver` / `mssql` | SqlServerDialect | `OFFSET ? FETCH NEXT ? ROWS ONLY` |
| 其他/未配置 | PostgreSqlDialect | 默认 |

`Dialect.appendPagination(sql, offset, size, params)` 按各方言正确处理参数顺序（如 Oracle 的 ROWNUM 双层子查询）。`SqlBuilder` 持有 dialect 字段，`DataAutoConfiguration` 从 `summer.datasource.dialect` 注册 `Dialect` bean。

## 数据源配置

`application.yml`（推荐）或 `application.properties`：

```yaml
summer:
  datasource:
    url: jdbc:postgresql://host:5432/postgres
    username: postgres
    password: 'secret'
    driver-class-name: org.postgresql.Driver
    pool-size: 4
    dialect: postgresql
    connection-timeout: 30000        # 借出等待超时（毫秒），池满时阻塞至此超时
    leak-detection-threshold: 60000  # 泄漏检测阈值（毫秒），0=关闭
```

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `pool-size` | 8 | 连接池固定大小 |
| `connection-timeout` | 30000 | 借出等待超时（毫秒），超时抛 `SQLException` |
| `leak-detection-threshold` | 0（关闭） | 连接持有超过此阈值时打 WARN 日志（含借出调用栈） |

- 内置轻量连接池（`ArrayBlockingQueue` + 动态代理 `Connection`，`close()` 归还连接），虚拟线程友好；
- 借出时校验 `isValid(2)`，失效连接自动重建；
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
1. 创建 `DataSource` + `SqlExecutor` + `Dialect` bean；
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
