# 多数据源（summer-data）

支持在同一个应用中配置多个数据源，通过注解在方法/类级别切换路由，零第三方依赖。

## 注解

| 注解 | 作用 |
| --- | --- |
| `@DS("name")` | 指定数据源名称（方法或类级别） |
| `@Master` | `@DS("master")` 的快捷别名 |
| `@Slave` | `@DS("slave")` 的快捷别名 |
| `@DSTransactional` | 多数据源事务（跨数据源统一提交/回滚） |

## 配置

单数据源（向后兼容，使用 `summer.datasource.*`）：

```yaml
summer:
  datasource:
    url: jdbc:postgresql://host:5432/db
    username: postgres
    password: secret
    driver-class-name: org.postgresql.Driver   # 方言由驱动类名自动映射，无需配置 dialect
```

多数据源（使用 `summer.datasources.<name>.*`）：

```yaml
summer:
  datasource:
    default: master          # 默认数据源名
  datasources:
    master:
      url: jdbc:postgresql://master-host:5432/db
      username: postgres
      password: secret
      driver-class-name: org.postgresql.Driver
      pool-size: 8
    slave:
      url: jdbc:postgresql://slave-host:5432/db
      username: postgres
      password: secret
      driver-class-name: org.postgresql.Driver
      pool-size: 4
    log-db:
      url: jdbc:mysql://log-host:3306/logs
      username: root
      password: secret
      driver-class-name: com.mysql.cj.jdbc.Driver
      pool-size: 2
```

配置了 `summer.datasources.*.url` 时自动启用多数据源模式，`DynamicDataSource` 作为唯一 `DataSource` bean 注册。

## 路由原理

```
@DS("slave") / @Slave 方法调用
    │
    ▼
DsInterceptor (order=200) → DsContext.push("slave")
    │
    ▼
SqlExecutor.open()
    │
    ▼
DynamicDataSource.getConnection()
    │  DsContext.current() = "slave"
    ▼
slave 数据源连接池.getConnection()
    │
    ▼
方法返回 → DsContext.pop() 恢复
```

- `DsContext` 用 ThreadLocal 栈保存路由键，支持嵌套 `@DS` 调用（内层覆盖外层，结束后恢复）；
- 未设置路由键时使用 `summer.datasource.default` 指定的默认数据源；
- `DynamicDataSource` 是唯一的 `DataSource` bean，`SqlExecutor` 和 `TransactionManager` 透明使用。

## 使用示例

```java
public interface OrderService {
    Order findById(Long id);
    void save(Order order);
}

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Slave                        // 读操作走从库
    @Override
    public Order findById(Long id) {
        return orderMapper.selectById(id);
    }

    @Master                       // 写操作走主库
    @Override
    public void save(Order order) {
        orderMapper.insert(order);
    }

    @DS("log-db")                 // 日志库
    public void logAccess(String user) {
        // 使用 log-db 数据源
    }
}
```

## @DSTransactional 多数据源事务

`@DSTransactional` 跨多个数据源管理事务：每个参与的数据源各开一个连接（autoCommit=false），方法正常结束统一提交，异常统一回滚。

```java
@DSTransactional(rollbackFor = Exception.class)
public void transferBetweenDbs(Long fromId, Long toId, BigDecimal amount) {
    // 默认走 master
    accountMapper.deduct(fromId, amount);
    // 切换到 log-db 记录日志（同一事务内）
    DsContext.push("log-db");
    try {
        logMapper.insertTransferLog(fromId, toId, amount);
    } finally {
        DsContext.pop();
    }
    // 方法结束：master + log-db 同时提交或同时回滚
}
```

| 特性 | 说明 |
| --- | --- |
| 隔离级别 | 每个数据源独立连接，autoCommit=false |
| 嵌套 | 不支持嵌套 `@DSTransactional`（最外层有效） |
| 提交 | 所有参与连接顺序提交；某源提交失败不影响已提交的源 |
| 回滚 | 异常时所有参与连接回滚 |
| 与 `@Transactional` | 互斥使用；`@DSTransactional` 用于跨源，`@Transactional` 用于单源 |

> 注意：这是 best-effort 多数据库事务（非 2PC/XA）。若一个数据源提交成功而另一个失败，已提交的数据不会回滚。

## 自动配置

`DataAutoConfiguration` 检测配置模式：
- 有 `summer.datasources.*.url` → 多数据源模式，创建 `DynamicDataSource` + `DsInterceptor` + `DsTransactionInterceptor`；
- 仅有 `summer.datasource.url` → 单数据源模式（向后兼容）；
- 都没有 → 返回 `LazyDataSource`（不连接）。

拦截器优先级：`DsInterceptor(200)` > `DsTransactionInterceptor(150)` > `TransactionInterceptor(100)`，保证路由在事务开启前设置。

## 验证

`MultiDsSmokeTest`（纯逻辑，无 DB，**8 项断言全过**）：
- 默认路由 → master
- `@Master` / `@Slave` / `@DS("log-db")` 路由正确
- 嵌套 `@DS` 调用后恢复上层路由
- `DsContext.clear()` 后回到默认