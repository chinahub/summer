# summer-data 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-data/` 目录下的文件时才会加载本文件。

## 模块职责

MyBatis-Plus 风格 ORM：BaseMapper/Wrapper/分页/IService/事务/多方言/多数据源，纯 JDBC 实现。依赖 summer-core。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.data.mapper` | Mapper：BaseMapper 接口 + MapperProxyFactory（JDK 动态代理） + MapperSupport |
| `cn.jiebaba.summe.data.conditions` | 条件构造：AbstractWrapper/QueryWrapper/LambdaQueryWrapper/SFunction |
| `cn.jiebaba.summe.data.page` | 分页：IPage/Page |
| `cn.jiebaba.summe.data.service` | Service：IService + ServiceImpl（Service 层模板） |
| `cn.jiebaba.summe.data.support` | 底层支撑：SqlExecutor/SqlBuilder/RowMapper/TypeHandler/DataSourceFactory/IdGenerator |
| `cn.jiebaba.summe.data.transaction` | 事务：@Transactional + TransactionManager/TransactionInterceptor |
| `cn.jiebaba.summe.data.datasource` | 多数据源：@DS/@Master/@Slave + DynamicDataSource + @DSTransactional |
| `cn.jiebaba.summe.data.metadata` | 元数据：TableInfo/TableFieldInfo/MetadataParser/NamingUtils |
| `cn.jiebaba.summe.data.dialect` | 方言：MySQL/PostgreSQL/Oracle/SQL Server |
| `cn.jiebaba.summe.data.annotation` | 注解：@TableName/@TableId/@TableField/@TableLogic |

## 模块约定

- **零第三方依赖**：纯 JDBC 实现，不依赖 MyBatis 或 Hibernate
- **Lambda 链式条件**：`LambdaQueryWrapper` 使用 `SFunction`（Serializable 函数式接口）实现类型安全的条件构造
- **Mapper 代理**：`MapperProxyFactory` 通过 JDK 动态代理将接口方法调用转为 SQL
- **多方言**：通过 `Dialect` 接口适配不同数据库的分页 SQL 和语法差异；覆盖 MySQL/PostgreSQL/Oracle/SQL Server（含 H2/SQLite 复用 MySQL 方言），未识别时回退 PostgreSQL 并告警
- **标识符按需转义**：`Dialect.quote` 仅对保留字/非法字符标识符加引号（Oracle 双引号转大写、SQL Server 方括号、MySQL 反引号、PG 双引号），`SqlBuilder` 全部 SQL 生成已接入；普通标识符保持裸名
- **Oracle/SQL Server 适配**：String 字段走 `rs.getString`（CLOB/NVARCHAR 正确回读）、时间字段走 `rs.getTimestamp`（规避 oracle.sql.TIMESTAMP）；keepalive 探活 SQL 按方言兜底（Oracle → `SELECT 1 FROM DUAL`）；主键仅支持 IDENTITY 列（Oracle 12c+），sequence 不支持
- **多数据源**：@DS 注解 + ThreadLocal 上下文，支持读写分离（@Master/@Slave）和跨源事务
