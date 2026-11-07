# summer-admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除独立 summer-sample 并保留其框架测试能力，同时交付一个使用 summer-boot 4.2.0、MySQL 8、React 和 Ant Design 的独立可运行 summer-admin 核心后台。

**Architecture:** 先将 sample 作为测试夹具内聚到 build-test，再建立不继承 summer-parent、不加入根聚合的 summer-admin。后端按业务包组织，使用 summer 自研 Web/JDBC/JWT/AOP；前端使用 Umi Max 动态菜单和预注册页面，开发分离、生产打入同一可执行 JAR。

**Tech Stack:** JDK 25、Maven、summer-boot 4.2.0、summer-pack-maven-plugin 4.2.0、MySQL Connector/J 8.4.0、MySQL 8.0.46、JUnit 5.12.2、React 19、TypeScript、Ant Design 6、Umi Max、Zustand、TanStack Query、Axios、Node 24、pnpm 10、Vitest。

**Spec:** `docs/superpowers/specs/2026-11-07-summer-admin-design.md`

## Global Constraints

- 所有文本使用 UTF-8 无 BOM、LF、恰好一个末尾换行；Java 使用 4 空格，超过 20 行的方法使用中文 JavaDoc。
- summer-admin 位于 `E:\summer_workspace\summer-admin`，属于当前 Git 仓库，但不初始化嵌套 Git，不继承 summer-parent，不加入根 `<modules>`。
- 根 `mvn clean package` 不构建 summer-admin；admin 使用 Maven Central 的 summer-boot `4.2.0`，不使用 `systemPath`、相对模块依赖或修改 summer-boot 运行时代码。
- 后端 GAV 为 `cn.jiebaba.summer:summer-admin:1.0.0`，主类为 `cn.jiebaba.summer.admin.SummerAdminApplication`。
- 默认开发库为 `summer_admin`，测试库只能为 `summer_admin_test`；测试不得清空开发库。
- 数据库密码、JWT 密钥通过环境变量提供，不把 `D:\WSL\MySQL8-Docker部署记录.md` 中的凭据复制进工程。
- API 统一 `/api` 前缀；普通响应为 `{code,msg,data}`，分页响应为 `{code,msg,rows,total}`，分页参数为 `pageNum/pageSize`。
- 超级管理员固定为 `user_id=1`；默认本地账号为 `admin/admin123` 和 `user/user123`，数据库只存 BCrypt 哈希。
- 第一阶段不包含多租户、Redis、在线用户、工作流、代码生成、任务管理、监控、OSS、注册、第三方登录和国际化。
- 权限变更重新登录后生效；不提供 refresh token、服务端令牌撤销或强制下线。
- 生产前端使用 hash history；生成文件只进入 `target` 或 `frontend/dist`，不得写回 `src/main/resources/static`。
- 工作区当前已有 summer-boot、build-test 和 ORM 文档等未提交修改；执行前必须核对归属并保留，不得用 reset/checkout 覆盖，也不得把无关修改纳入本计划提交。
- 每个任务结束只提交该任务文件；执行计划前确认用户允许按本计划创建这些本地提交，不推送远端。

## Review Focus

- 空角色、空自定义部门集合或仅 `SELF` 的数据范围必须缩小权限，不能因条件为空而退化为全表查询；由 Task 5 的 `DataScopeResolverTest` 固定。
- 初始化 SQL 重跑必须保留已有业务修改，空数据库和二次执行都要成功；由 Task 3 的 `SqlInitializationTest` 固定。
- 负数、零和超大 `pageNum/pageSize` 必须被规范化且不能产生负 OFFSET 或无限大查询；由 Task 4 的 `PageQueryTest` 固定。
- 密码、验证码、Authorization 和日志组件自身异常不得泄露秘密或改变业务结果；由 Task 7、Task 11 的安全测试固定。
- 后端返回未知或白名单外前端组件时必须显示安全 404，而非动态加载任意模块或白屏；由 Task 12 的 `pageRegistry.test.ts` 固定。

---

### Task 1: 将 summer-sample 内聚为 build-test 测试夹具

**Files:**
- Modify: `pom.xml:26-35`
- Modify: `build-test/pom.xml:21-84`
- Move: `summer-sample/src/main/**` → `build-test/src/main/**`
- Move/merge: `summer-sample/src/test/java/**` → `build-test/src/main/java/**`（build-test 的 surefire 扫描主输出目录，确保三个 office 测试仍被执行）
- Merge: `summer-sample/src/main/resources/application.yml` → `build-test/src/main/resources/application.yml`
- Modify: `AGENTS.md`
- Modify: `build-test/AGENTS.md`
- Delete: `summer-sample/`（仅在前述迁移和检查完成后）

**Interfaces:**
- Consumes: 现有 `cn.jiebaba.summer.sample.Application` 及各 sample 测试路由。
- Produces: build-test 内部自包含的 sample 测试夹具；保留包名 `cn.jiebaba.summer.sample`，不再发布 `summer-sample` Maven 构件。

- [ ] **Step 1: 记录迁移前测试基线**

Run:

```bash
export JAVA_HOME="D:\jdk\jdk-25.0.4" && export PATH="/d/jdk/jdk-25.0.4/bin:$PATH"
mvn -pl build-test -am -DskipTests compile
```

Expected: BUILD SUCCESS；若失败，记录与任务无关的既有错误，不通过删测试规避。

- [ ] **Step 2: 迁移 sample 并解除 Maven 依赖**

移动源码、资源和测试，合并 application 配置中的 `server`、`summer.security`、`summer.datasource` 和 AI 示例配置；保留 build-test 原有项。删除根 module 声明、build-test 的 sample 依赖及失效注释，保留 build-test 的 PostgreSQL 等真实测试驱动。

- [ ] **Step 3: 更新模块约定并检查残留引用**

更新根 AGENTS 的模块数量/表格和 build-test AGENTS 的职责/依赖。用 `Grep` 检查 build-test 与根 POM 不再有 `summer-sample` 构件依赖；仅允许文档历史说明或“已迁移”表述。确认无文件覆盖丢失后删除 `summer-sample/`。

- [ ] **Step 4: 验证根工程和原冒烟宿主**

Run:

```bash
mvn -pl build-test -am -DskipTests compile
mvn -pl build-test -am test
mvn -pl build-test -DskipTests -Dexec.mainClass=cn.jiebaba.summer.test.SmokeTest org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

Expected: 编译成功，三个迁移的 office JUnit 测试仍被发现且无新增失败；SmokeTest 原 11 个路由断言通过。真实 PG 等外部测试若基线已失败，单独记录环境原因。

- [ ] **Step 5: Commit**

```bash
git add pom.xml AGENTS.md build-test summer-sample
git commit -m "refactor: move sample fixtures into build-test"
```

### Task 2: 建立可独立构建的 summer-admin 后端骨架

**Files:**
- Create: `summer-admin/pom.xml`
- Create: `summer-admin/.gitignore`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/SummerAdminApplication.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/web/HealthController.java`
- Create: `summer-admin/src/main/resources/application.yml`
- Create: `summer-admin/src/test/java/cn/jiebaba/summer/admin/ApplicationSmokeTest.java`

**Interfaces:**
- Consumes: summer-boot 4.2.0 的 `SummerApplication.run` 和 Web 自动配置。
- Produces: `GET /api/health` 暂返回 `Map.of("status", "UP")`，Task 4 再统一为 `R.ok(...)`；后续任务复用同一应用入口和测试端口启动方式。

- [ ] **Step 1: 写失败的启动测试**

`ApplicationSmokeTest` 启动随机端口应用，使用 Java `HttpClient` 断言 `/api/health` 为 200 且 JSON 含 `"status":"UP"`；测试本身不得读取 summer-admin 之外的根 POM，以保证复制到临时目录后仍可独立运行。

- [ ] **Step 2: 运行测试确认骨架尚不存在**

Run: `mvn -f summer-admin/pom.xml -Dtest=ApplicationSmokeTest test`  
Expected: Maven 报告 POM 不存在，证明测试尚未被空实现跳过。

- [ ] **Step 3: 实现独立 POM、配置和健康端点**

POM 固定 Java 25、summer-boot 4.2.0、MySQL Connector/J 8.4.0、JUnit 5.12.2、surefire 的 `forkCount=0` 和 `useManifestOnlyJar=false`。application.yml 默认端口 8080、MySQL URL `jdbc:mysql://localhost:3306/summer_admin?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`；骨架阶段暂设 `summer.security.enabled=false` 以便健康测试，Task 7 必须开启并收紧；不在健康端点提前引入数据库 Bean。

- [ ] **Step 4: 验证独立构建与健康接口**

Run: `mvn -f summer-admin/pom.xml -Dtest=ApplicationSmokeTest test`  
Expected: 测试通过；另在临时目录复制 summer-admin 后执行同一命令，证明无仓库相对依赖。回到原仓库用 Grep 确认根 `<modules>` 不含 summer-admin、独立 POM 无 summer-parent。

- [ ] **Step 5: Commit**

```bash
git add summer-admin
git commit -m "feat: add independent summer-admin backend skeleton"
```

### Task 3: 建立 MySQL 架构、种子数据、实体与 Mapper

**Files:**
- Create: `summer-admin/sql/summer_admin.sql`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/persistence/BaseEntity.java`
- Create: `system/dept/{SysDept.java,SysDeptMapper.java}`（下列相对路径均以 `summer-admin/src/main/java/cn/jiebaba/summer/admin/` 为根）
- Create: `system/post/{SysPost.java,SysPostMapper.java,SysUserPost.java,SysUserPostMapper.java}`
- Create: `system/role/{SysRole.java,SysRoleMapper.java,SysUserRole.java,SysUserRoleMapper.java,SysRoleDept.java,SysRoleDeptMapper.java,SysRoleMenu.java,SysRoleMenuMapper.java}`
- Create: `system/menu/{SysMenu.java,SysMenuMapper.java}`
- Create: `system/user/{SysUser.java,SysUserMapper.java}`
- Create: `system/dict/{SysDictType.java,SysDictTypeMapper.java,SysDictData.java,SysDictDataMapper.java}`
- Create: `system/config/{SysConfig.java,SysConfigMapper.java}`
- Create: `system/notice/{SysNotice.java,SysNoticeMapper.java}`
- Create: `system/log/{SysOperLog.java,SysOperLogMapper.java,SysLoginLog.java,SysLoginLogMapper.java}`
- Create: `summer-admin/src/test/java/cn/jiebaba/summer/admin/testsupport/MySqlTestDatabase.java`
- Create: `summer-admin/src/test/java/cn/jiebaba/summer/admin/SqlInitializationTest.java`
- Create: `summer-admin/src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: 本机 `localhost:3306` MySQL 8；连接用户通过 `SUMMER_DATASOURCE_USERNAME/PASSWORD` 注入。
- Produces: 设计 §6.2 的 15 张表及对应 `BaseMapper<T>`；`BaseEntity` 提供 `createBy/createTime/updateBy/updateTime`；`MySqlTestDatabase.reset()` 只重建 `summer_admin_test` 并执行同一 SQL。

- [ ] **Step 1: 写失败的 SQL 初始化测试**

`SqlInitializationTest` 依次断言：空测试库执行成功；15 张表存在；admin 的 `user_id=1` 且密码以 `$2` 开头；第二次执行成功；将 admin 昵称改为 `KeepMe` 后重跑仍为 `KeepMe`；测试库名不等于 `summer_admin`。

- [ ] **Step 2: 运行测试确认 SQL 缺失**

Run: `mvn -f summer-admin/pom.xml -Dtest=SqlInitializationTest test`  
Expected: FAIL，原因为 SQL 资源不存在。

- [ ] **Step 3: 实现可重跑 SQL 和完整持久化模型**

SQL 使用 `CREATE DATABASE IF NOT EXISTS`、`CREATE TABLE IF NOT EXISTS` 和带固定自然键/主键的幂等种子语句，不使用无条件 `DROP TABLE` 或覆盖昵称的重复更新。建立必要唯一索引、关系索引和外键。实体使用 `@TableName/@TableId/@TableField/@TableLogic`，关系表和日志不虚构逻辑删除字段。

- [ ] **Step 4: 验证 SQL 和 Mapper 元数据**

Run: `mvn -f summer-admin/pom.xml -Dtest=SqlInitializationTest test`  
Expected: PASS；再用每个 Mapper 对空关系表执行 `selectCount` 的 smoke 断言，确认字段映射无误。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/sql summer-admin/src
git commit -m "feat: add admin database schema and persistence model"
```

### Task 4: 统一 API 响应、分页和异常契约

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/api/R.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/api/TableData.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/api/PageQuery.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/exception/BusinessException.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/exception/GlobalExceptionHandler.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/exception/ApiErrorResponseFilter.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/validation/RequestValidator.java`
- Modify: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/web/HealthController.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/common/api/PageQueryTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/common/api/ApiContractTest.java`

**Interfaces:**
- Consumes: `cn.jiebaba.summer.data.page.IPage<T>`、`WebResponse` 和 `cn.jiebaba.summer.web.validation.Validator`。
- Produces: `R<T>`、`R.ok(T)`、`R.ok()`、`R.failure(int,String)`；`TableData<T>.of(IPage<T>)`；`PageQuery.toPage()`；`BusinessException(int status,String message)`；`RequestValidator.requireValid(Object)`。

- [ ] **Step 1: 写失败的分页和异常测试**

`PageQueryTest` 断言 `(0,0) → current=1,size=10`、`(-5,10000) → current=1,size=100`，offset 永不为负。`ApiContractTest` 断言业务 409、注解校验 400、未知 API 404、认证 401、授权 403、未知异常 500 的统一 `{code,msg}` 字段，且 500 不含堆栈、SQL 或敏感请求体；401/403 在 Task 7 安全链启用后补真实请求断言。

- [ ] **Step 2: 运行并确认缺少契约类型**

Run: `mvn -f summer-admin/pom.xml -Dtest=PageQueryTest,ApiContractTest test`  
Expected: 编译失败，相关类型不存在。

- [ ] **Step 3: 实现契约、显式校验和全局异常映射**

`PageQuery.toPage()` 将 pageNum 最小规范为 1、pageSize 默认 10 且限制 1..100。`TableData.of` 使用 `records()` 和 `total()`。`RequestValidator` 调用 `Validator.validate` 并把字段消息转成 BusinessException(400)，控制器不在参数上使用会触发框架内置错误格式的 `@Valid`。`GlobalExceptionHandler.handleBusiness(BusinessException ex,WebResponse response)` 显式设置动态状态；未知异常仅返回安全通用消息并记录服务端日志。`ApiErrorResponseFilter` 作为普通过滤器包住安全链和路由，在 `/api` 返回 4xx/5xx 后把框架内置 401/403/404/校验错误规范为 R，已经是 R 的响应不重复改写；HealthController 同步改用 R.ok。

- [ ] **Step 4: 验证 API 契约**

Run: `mvn -f summer-admin/pom.xml -Dtest=PageQueryTest,ApiContractTest,ApplicationSmokeTest test`  
Expected: PASS，健康接口改用 R 后仍符合原断言。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: standardize admin api contracts"
```

### Task 5: 实现部门、岗位与数据范围解析

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/security/DataScopeType.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/security/DataScopeRule.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/security/DataScopeResolver.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dept/DeptService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dept/DeptController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/post/PostService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/post/PostController.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/security/DataScopeResolverTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/dept/DeptServiceTest.java`

**Interfaces:**
- Consumes: Task 3 的 user/role/dept/roleDept Mapper 和 Task 4 的 BusinessException。
- Produces: `DataScopeResolver.resolve(Long userId): DataScopeRule`；`DataScopeRule.applyToUser(LambdaQueryWrapper<SysUser>,Long currentUserId)` 和 `assertVisible(SysUser,Long currentUserId)`；部门树和岗位 CRUD。

- [ ] **Step 1: 写五种数据范围及失败边界测试**

使用固定内存 Mapper/仓库测试数据覆盖 ALL、DEPT、DEPT_AND_CHILDREN、SELF、CUSTOM；断言多角色取并集、任一 ALL 无限制、空自定义集合和空角色返回永假条件、SELF 与 DEPT 同时存在时为 OR、越权 ID 抛出 403。`DeptServiceTest` 断言有子节点/用户禁止删除，移动部门会更新全部后代 ancestors 且禁止形成环。

- [ ] **Step 2: 运行并确认解析器不存在**

Run: `mvn -f summer-admin/pom.xml -Dtest=DataScopeResolverTest,DeptServiceTest test`  
Expected: 编译失败，相关类型不存在。

- [ ] **Step 3: 实现数据范围和部门/岗位服务**

`DataScopeRule` 用 `unrestricted`、`deptIds`、`includeSelf` 表示范围，不用空集合代表无限制。应用用户查询时使用预编译 `in/eq` 和嵌套 OR；按 ID 操作必须调用 `assertVisible`。部门 Service 用事务维护父子路径和排序；岗位提供分页 CRUD、唯一性校验及引用检查。

- [ ] **Step 4: 验证权限不会扩大**

Run: `mvn -f summer-admin/pom.xml -Dtest=DataScopeResolverTest,DeptServiceTest test`  
Expected: PASS，额外断言生成 Wrapper 的 `sqlSegment()` 在空权限时包含 `1=0`。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add organization management and data scopes"
```

### Task 6: 实现角色、菜单和动态路由模型

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/menu/MenuService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/menu/MenuController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/menu/RouterVo.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/role/RoleService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/role/RoleController.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/menu/MenuServiceTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/role/RoleServiceTest.java`

**Interfaces:**
- Consumes: Task 5 的 `DataScopeType`；Task 3 的 role/menu/roleMenu/roleDept Mapper。
- Produces: `MenuService.permissionsForUser(Long userId): Set<String>`、`routersForUser(Long userId): List<RouterVo>`；角色 CRUD、`assignMenus(Long roleId,Set<Long> menuIds)`、`assignDataScope(Long roleId,DataScopeType scopeType,Set<Long> deptIds)`。

- [ ] **Step 1: 写失败的权限聚合和关系事务测试**

断言 admin 得到所有有效权限/路由，普通用户只得到启用角色并集，按钮不进入 RouterVo；停用角色/菜单被排除。角色测试断言菜单和自定义部门关系替换在同一事务，失败时不出现半套关系；内置超级角色不可删除。

- [ ] **Step 2: 运行测试确认服务缺失**

Run: `mvn -f summer-admin/pom.xml -Dtest=MenuServiceTest,RoleServiceTest test`  
Expected: 编译失败。

- [ ] **Step 3: 实现角色菜单服务和控制器**

菜单类型限定目录/菜单/按钮，校验父子关系、路由和权限标识。角色保存数据范围五枚举，CUSTOM 必须显式保存 roleDept 集合；其他类型清空自定义集合。所有写端点加精确 `@PreAuthorize(authorities={"system:role:edit"})` 等权限。

- [ ] **Step 4: 验证动态菜单与事务**

Run: `mvn -f summer-admin/pom.xml -Dtest=MenuServiceTest,RoleServiceTest,DataScopeResolverTest test`  
Expected: PASS，路由树顺序和权限集合稳定。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add role menu and dynamic router services"
```

### Task 7: 打通数据库用户、验证码、JWT 与登录日志

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/AdminUserDetails.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/DatabaseUserDetailsService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/SecurityConfig.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/captcha/CaptchaStore.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/captcha/InMemoryCaptchaStore.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/captcha/CaptchaService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/AuthService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/AuthController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/dto/LoginRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/auth/dto/LoginResult.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/log/LoginLogService.java`
- Modify: `summer-admin/src/main/resources/application.yml`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/auth/CaptchaServiceTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/auth/AuthApiTest.java`

**Interfaces:**
- Consumes: `AuthenticationManager`、`JwtEncoder/JwtDecoder/JwtClaims` 和 Task 6 的 `permissionsForUser`。
- Produces: `CaptchaService.issue()`、`verify(String uuid,String code)`；`AuthService.login(LoginRequest,String ip,String userAgent): LoginResult`；`/api/auth/captcha|login|logout|userinfo|routers`。

- [ ] **Step 1: 写失败的认证和一次性验证码测试**

`CaptchaServiceTest` 覆盖错误码、两分钟过期、成功后重放失败、失败尝试按设计消费、不同 UUID 隔离。`AuthApiTest` 覆盖正确/错误密码、停用账号、无 token 401、无权限 403、登出需 token；登录/验证码失败均写 `sys_login_log`，响应和日志不含密码/验证码。

- [ ] **Step 2: 运行并确认认证链路缺失**

Run: `mvn -f summer-admin/pom.xml -Dtest=CaptchaServiceTest,AuthApiTest test`  
Expected: 编译失败。

- [ ] **Step 3: 实现自定义登录且复用框架安全链**

将 application.yml 的 `summer.security.enabled` 改为 true。数据库 UserDetails 加载账号状态、角色和菜单权限，admin 加载全部有效权限。SecurityConfig 仅放行 `/api/health`、captcha/login 和 `/`、`/index.html`、`/assets/**`、`/static/**`、`/*.js`、`/*.css`、`/favicon.ico` 等实际构建静态资源，JWT 配置 encoder/decoder 但不传 AuthenticationManager，避免内置登录过滤器；logout/userinfo/routers 需要认证。AuthService 使用 AuthenticationManager 认证后构建 `JwtClaims.TYPE_ACCESS`，默认 TTL 7200 秒。

- [ ] **Step 4: 实现单实例验证码与登录日志**

使用 JDK BufferedImage 生成 Base64 图片；ConcurrentHashMap 保存 UUID、不可逆摘要和过期时间，验证即消费并清理过期项。测试配置可关闭验证码以验证完整登录 API，验证码本身由独立单元测试覆盖。登出只记录操作，不声称服务端撤销。

- [ ] **Step 5: 验证真实 HTTP 认证**

Run: `mvn -f summer-admin/pom.xml -Dtest=CaptchaServiceTest,AuthApiTest test`  
Expected: PASS；手工解码测试 token 仅检查 sub/authorities/iat/exp/typ，不检查或记录真实密钥。

- [ ] **Step 6: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add database login captcha and jwt security"
```

### Task 8: 实现用户管理、关系分配和个人中心

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/UserService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/UserController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/dto/CreateUserRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/dto/UpdateUserRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/dto/ResetPasswordRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/dto/UpdateProfileRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/dto/ChangePasswordRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/dto/UserVo.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/user/ProfileController.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/user/UserServiceTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/user/UserApiTest.java`

**Interfaces:**
- Consumes: Task 5 的 DataScopeRule、Task 6 的角色菜单关系、Task 7 的认证主体。
- Produces: 用户分页/详情/新增/修改/删除、角色岗位分配、状态修改、重置密码；`/api/system/profile` 资料与密码端点。

- [ ] **Step 1: 写失败的用户事务和越权测试**

覆盖新增用户及角色岗位关系、更新替换关系、失败回滚、登录名唯一、列表部门/角色名称批量组装；覆盖五种数据范围的列表与按 ID 越权、禁止删除/禁用 user_id=1、禁止删除自身、普通用户不能分配超出自身可见范围的用户。

- [ ] **Step 2: 运行并确认用户服务缺失**

Run: `mvn -f summer-admin/pom.xml -Dtest=UserServiceTest,UserApiTest test`  
Expected: 编译失败。

- [ ] **Step 3: 实现用户 DTO、事务服务和控制器**

密码只在新增/重置 DTO 输入，任何 VO 不包含 password；关系查询批量建立 role/post/dept 映射。个人资料仅允许白名单字段，修改密码必须验证原密码并重新 BCrypt 编码；管理端重置密码使用独立权限。

- [ ] **Step 4: 验证 CRUD、数据权限和秘密字段**

Run: `mvn -f summer-admin/pom.xml -Dtest=UserServiceTest,UserApiTest test`  
Expected: PASS；序列化响应递归检查无 `password`、`captcha`、`Authorization`。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add user administration and profile"
```

### Task 9: 实现字典类型和字典数据

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dict/DictService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dict/DictController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dict/dto/DictTypeRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dict/dto/DictDataRequest.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/dict/dto/DictDataVo.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/dict/DictServiceTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/dict/DictApiTest.java`

**Interfaces:**
- Consumes: Task 3 的 dict Mapper 和 Task 4 的 API 契约。
- Produces: 字典类型/数据 CRUD；`DictService.enabledItems(String dictType): List<SysDictData>` 供前端按类型读取启用项。

- [ ] **Step 1: 写失败字典测试**

断言类型唯一、同类型字典值唯一、仅返回启用且排序正确的数据、有数据的类型拒绝删除、普通查询无修改权限。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -f summer-admin/pom.xml -Dtest=DictServiceTest,DictApiTest test`  
Expected: 编译失败。

- [ ] **Step 3: 实现字典服务和 REST 端点**

路径使用 `/api/system/dict/types` 和 `/api/system/dict/data`，公开给已认证用户的只有按类型读取启用字典；维护操作使用 `system:dict:*` 权限。

- [ ] **Step 4: 运行验证**

Run: `mvn -f summer-admin/pom.xml -Dtest=DictServiceTest,DictApiTest test`  
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add dictionary management"
```

### Task 10: 实现系统参数、通知公告和首页汇总

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/config/ConfigService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/config/ConfigController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/notice/NoticeService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/notice/NoticeController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/dashboard/DashboardService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/dashboard/DashboardController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/dashboard/DashboardSummary.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/config/ConfigServiceTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/notice/NoticeServiceTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/dashboard/DashboardServiceTest.java`

**Interfaces:**
- Consumes: 对应 Mapper、分页和权限契约。
- Produces: `/api/system/configs`、`/api/system/notices` 的分页 CRUD；`DashboardService.summary(): DashboardSummary` 和 `GET /api/dashboard/summary`，提供真实用户、角色、部门、已发布公告数量。

- [ ] **Step 1: 写失败业务测试**

参数测试覆盖键唯一、空键、分页和修改；公告测试覆盖标题必填、草稿不进入普通可见列表、发布状态切换和按时间/排序稳定返回；DashboardServiceTest 使用固定 Mapper 数据断言四项计数，逻辑删除用户和草稿公告不计入。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -f summer-admin/pom.xml -Dtest=ConfigServiceTest,NoticeServiceTest,DashboardServiceTest test`  
Expected: 编译失败。

- [ ] **Step 3: 实现参数、公告和首页汇总服务**

参数第一阶段直接读取数据库，不增加缓存刷新假按钮。公告管理端可查看草稿，普通已认证用户只看到已发布公告；写端点分别使用 `system:config:*`、`system:notice:*` 权限。DashboardController 只要求登录，通过 Mapper count 聚合真实数量，不返回监控或敏感字段。

- [ ] **Step 4: 运行验证**

Run: `mvn -f summer-admin/pom.xml -Dtest=ConfigServiceTest,NoticeServiceTest,DashboardServiceTest test`  
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add config and notice management"
```

### Task 11: 实现操作日志和两类日志查询

**Files:**
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/log/OperLog.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/log/OperationLogAspect.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/log/LogQueryService.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/system/log/LogController.java`
- Create: `summer-admin/src/main/java/cn/jiebaba/summer/admin/common/log/SensitiveDataSanitizer.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/common/log/OperationLogAspectTest.java`
- Test: `summer-admin/src/test/java/cn/jiebaba/summer/admin/system/log/LogQueryServiceTest.java`

**Interfaces:**
- Consumes: 当前 SecurityContext、SysOperLog/SysLoginLog Mapper、AOP。
- Produces: `@OperLog(module,action)`；`/api/system/logs/operations`、`/api/system/logs/logins` 的分页/详情/删除/清空。

- [ ] **Step 1: 写失败的日志安全与查询测试**

Aspect 测试覆盖成功、业务异常、耗时、操作人和 IP；请求中的 password/code/token/Authorization 被替换为 `******`；日志 Mapper 抛错时原返回值/原异常不变且不会递归记录。查询测试覆盖条件分页、详情、批量删除、清空，清空自身也产生一条操作日志。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -f summer-admin/pom.xml -Dtest=OperationLogAspectTest,LogQueryServiceTest test`  
Expected: 编译失败。

- [ ] **Step 3: 实现注解、切面、脱敏和日志端点**

切面只拦截显式 @OperLog 方法，序列化前按大小写无关敏感键递归脱敏并限制请求/异常摘要长度。日志保存用独立 try/catch 隔离；查询和清理使用 `monitor:log:*` 权限，清空后记录一个不依赖旧事务的审计事件。

- [ ] **Step 4: 为已有管理写操作补注解并做全量后端回归**

只给新增、修改、删除、分配、重置密码、清日志等写操作加注解。Run: `mvn -f summer-admin/pom.xml test`  
Expected: 所有现有后端测试 PASS，操作日志无递归和敏感值。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/src
git commit -m "feat: add operation and login audit logs"
```

### Task 12: 建立前端工程、请求契约和权限路由基础

**Files:**
- Create: `summer-admin/frontend/package.json`
- Create: `summer-admin/frontend/pnpm-lock.yaml`
- Create: `summer-admin/frontend/config/config.ts`
- Create: `summer-admin/frontend/vite.config.ts`
- Create: `summer-admin/frontend/tsconfig.json`
- Create: `summer-admin/frontend/src/app.tsx`
- Create: `summer-admin/frontend/src/api/{request.ts,types.ts,login.ts}`
- Create: `summer-admin/frontend/src/utils/{auth.ts,permission.ts,menu.ts}`
- Create: `summer-admin/frontend/src/stores/{appStore.ts,userStore.ts,permissionStore.ts,tagsViewStore.ts}`
- Create: `summer-admin/frontend/src/pages/pageRegistry.ts`
- Create: `summer-admin/frontend/src/pages/index.tsx`（本任务仅占位，Task 13 完成）
- Create: `summer-admin/frontend/src/pages/error/404.tsx`
- Test: `summer-admin/frontend/src/utils/permission.test.ts`
- Test: `summer-admin/frontend/src/pages/pageRegistry.test.ts`

**Interfaces:**
- Consumes: 后端 `{code,msg,data}`、`{code,msg,rows,total}` 和 RouterVo。
- Produces: Axios `request`、token 存取、`hasPermission`、`filterAuthorizedMenus`、`resolvePage(component)`；后续页面只使用 pageRegistry 白名单。

- [ ] **Step 1: 建立工具链并写失败的纯函数测试**

package.json 固定 dev/build/lint/test 脚本，Vitest 覆盖 token 401 清理、业务 code 非 200 拒绝、权限匹配、菜单过滤；`pageRegistry.test.ts` 断言未知组件、空组件和 `../` 路径返回安全 404 映射，当前已注册键都指向本任务已创建的文件，后续任务各自补充页面键。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd summer-admin/frontend && pnpm install && pnpm test`  
Expected: FAIL，相关实现文件不存在。

- [ ] **Step 3: 实现请求、状态、权限和页面注册表**

Umi 使用 hash history、pnpm、开发端口 8000 和 `/api → http://localhost:8080` 代理。Axios 只从 `data.access_token` 读取登录结果，401 清理 token 并回登录页；组件映射使用静态 import/lazy 白名单，不使用后端字符串构造动态 import。

- [ ] **Step 4: 验证测试、类型和生产编译**

Run: `pnpm test && pnpm lint && pnpm build:prod`  
Expected: 全部退出码 0，lockfile 可复现；不引入 Vue/Element Plus。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/frontend
git commit -m "feat: add react admin frontend foundation"
```

### Task 13: 实现登录页、管理布局、首页和个人中心

**Files:**
- Create: `summer-admin/frontend/src/pages/login.tsx`
- Modify: `summer-admin/frontend/src/pages/index.tsx`
- Create: `summer-admin/frontend/src/pages/dynamicPage.tsx`
- Create: `summer-admin/frontend/src/pages/error/403.tsx`
- Modify: `summer-admin/frontend/src/pages/error/404.tsx`
- Create: `summer-admin/frontend/src/layouts/BasicLayout.tsx`
- Create: `summer-admin/frontend/src/components/layout/{TagsView.tsx,KeepAliveTabs.tsx,LayoutSettings.tsx,Screenfull.tsx}`
- Create: `summer-admin/frontend/src/pages/system/user/profile/index.tsx`
- Create: `summer-admin/frontend/src/api/system/profile.ts`
- Create: `summer-admin/frontend/src/api/dashboard.ts`
- Modify: `summer-admin/frontend/src/pages/pageRegistry.ts`
- Modify: `summer-admin/frontend/config/config.ts`
- Test: `summer-admin/frontend/src/utils/menu.test.ts`
- Test: `summer-admin/frontend/src/stores/tagsViewStore.test.ts`

**Interfaces:**
- Consumes: Task 12 的 request/stores/registry 和 Task 7/8 的 auth/profile API。
- Produces: 登录到动态菜单的完整布局、hash 路由、多标签页、主题设置、首页真实汇总和个人中心。

- [ ] **Step 1: 写失败的路由与标签页状态测试**

`menu.test.ts` 覆盖后端树扁平化、父子路径、外部链接拒绝不安全协议、当前路由匹配；标签状态测试覆盖重复打开不重复、关闭当前标签后选择相邻标签、刷新保留其他标签。

- [ ] **Step 2: 运行确认失败**

Run: `cd summer-admin/frontend && pnpm test`  
Expected: FAIL。

- [ ] **Step 3: 实现登录和 BasicLayout**

登录页只含账号、密码、验证码、记住用户名；登录成功保存 access_token 并加载 userinfo/routers。布局实现菜单折叠、用户菜单、多标签、主题色、明暗、侧栏/顶部/混合、全屏；移除注册、第三方登录、语言切换和无后端能力的入口。个人中心实现白名单资料和原密码改密。

- [ ] **Step 4: 实现首页、动态页和错误页**

首页从真实 API 获取汇总；dynamicPage 通过 `resolvePage` 渲染白名单组件，未知组件显示 404；无权限显示 403。完成加载、空数据、请求失败和重复提交状态。

- [ ] **Step 5: 验证前端基础链路**

Run: `pnpm test && pnpm lint && pnpm build:prod`  
Expected: PASS。启动后端与 `pnpm dev`，浏览器完成 admin 登录、退出、过期 token 回登录页、个人资料/密码修改及布局操作。

- [ ] **Step 6: Commit**

```bash
git add summer-admin/frontend
git commit -m "feat: add admin login layout and profile pages"
```

### Task 14: 实现组织、用户、角色和菜单前端

**Files:**
- Create: `summer-admin/frontend/src/api/system/{user.ts,role.ts,menu.ts,dept.ts,post.ts}`
- Create: `summer-admin/frontend/src/pages/system/user/index.tsx`
- Create: `summer-admin/frontend/src/pages/system/role/index.tsx`
- Create: `summer-admin/frontend/src/pages/system/menu/index.tsx`
- Create: `summer-admin/frontend/src/pages/system/dept/index.tsx`
- Create: `summer-admin/frontend/src/pages/system/post/index.tsx`
- Create: `summer-admin/frontend/src/components/system/{UserForm.tsx,RoleMenuTree.tsx,RoleDataScope.tsx,ResetPasswordDialog.tsx,DeptTreeSelect.tsx}`
- Modify: `summer-admin/frontend/src/pages/pageRegistry.ts`
- Test: `summer-admin/frontend/src/pages/system/systemPages.test.ts`

**Interfaces:**
- Consumes: Task 5/6/8 后端接口、Task 12 注册表和权限函数。
- Produces: 五个完整管理页面及角色菜单/数据范围、用户角色岗位分配等共享表单组件。

- [ ] **Step 1: 写失败的页面注册和表单转换测试**

断言五个后端 component 键均映射页面；用户表单 roleIds/postIds、角色 menuIds/deptIds 和数据范围枚举与后端 DTO 完全一致，CUSTOM 无部门时前端阻止提交。

- [ ] **Step 2: 运行确认失败**

Run: `cd summer-admin/frontend && pnpm test`  
Expected: FAIL。

- [ ] **Step 3: 实现五个管理页面**

用户页包含部门树筛选、分页、角色/岗位、状态和重置密码；角色页包含菜单树及五种数据范围；菜单/部门使用树表格；岗位使用标准分页 CRUD。所有按钮有权限控制、加载、校验、删除确认和错误反馈，前端校验不替代后端。

- [ ] **Step 4: 验证类型、测试和真实 CRUD**

Run: `pnpm test && pnpm lint && pnpm build:prod`  
Expected: PASS。浏览器以 admin 完成五个模块 CRUD，以普通用户确认按钮隐藏和直接 URL 403。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/frontend
git commit -m "feat: add organization and permission management pages"
```

### Task 15: 实现字典、参数、公告和日志前端

**Files:**
- Create: `summer-admin/frontend/src/api/system/{dict.ts,config.ts,notice.ts,log.ts}`
- Create: `summer-admin/frontend/src/pages/system/dict/index.tsx`
- Create: `summer-admin/frontend/src/pages/system/dict/data.tsx`
- Create: `summer-admin/frontend/src/pages/system/config/index.tsx`
- Create: `summer-admin/frontend/src/pages/system/notice/index.tsx`
- Create: `summer-admin/frontend/src/pages/monitor/log/operation.tsx`
- Create: `summer-admin/frontend/src/pages/monitor/log/login.tsx`
- Create: `summer-admin/frontend/src/hooks/useDict.ts`
- Modify: `summer-admin/frontend/src/pages/pageRegistry.ts`
- Test: `summer-admin/frontend/src/pages/system/utilityPages.test.ts`

**Interfaces:**
- Consumes: Task 9/10/11 API 和统一分页契约。
- Produces: 字典主从管理、参数、公告、操作/登录日志页面和 `useDict(types)`。

- [ ] **Step 1: 写失败字典 Hook 和页面注册测试**

断言字典按类型缓存、启用项顺序保持、请求失败可重试；六个页面 component 键均注册，日志清空需要二次确认且不使用 GET。

- [ ] **Step 2: 运行确认失败**

Run: `cd summer-admin/frontend && pnpm test`  
Expected: FAIL。

- [ ] **Step 3: 实现六个管理页面和字典 Hook**

字典类型与数据可主从跳转；公告支持草稿/发布状态；日志提供条件分页、详情、删除和清空，敏感字段只展示后端脱敏结果。不得增加未实现的导出、监控或缓存刷新按钮。

- [ ] **Step 4: 验证前端全量测试和真实页面**

Run: `pnpm test && pnpm lint && pnpm build:prod`  
Expected: PASS。浏览器完成字典、参数、公告 CRUD，执行操作后能在两类日志页查询到对应记录。

- [ ] **Step 5: Commit**

```bash
git add summer-admin/frontend
git commit -m "feat: add dictionary notice config and log pages"
```

### Task 16: 集成生产单 JAR 与端到端验收

**Files:**
- Modify: `summer-admin/pom.xml`
- Create: `summer-admin/src/test/java/cn/jiebaba/summer/admin/ProductionJarIT.java`
- Create: `summer-admin/src/test/java/cn/jiebaba/summer/admin/e2e/CoreAdminE2eIT.java`
- Modify: `summer-admin/frontend/config/config.ts`

**Interfaces:**
- Consumes: frontend/dist、summer-pack-maven-plugin 和完整后端。
- Produces: 默认 `mvn clean package` 一次性构建前后端；`mvn clean verify` 在 package 后通过 failsafe 执行 `*IT`；可执行 `summer-admin-1.0.0-boot.jar` 同源提供 `/`、`/#/...` 和 `/api`。

- [ ] **Step 1: 写失败的生产产物集成测试并配置 failsafe**

`ProductionJarIT` 检查 JAR 含 `BOOT-INF/classes/static/index.html` 和 hash 资源，启动后 `/` 200、`/api/health` 200、未知 `/api/not-found` 不返回 index.html。POM 先只加入 maven-failsafe-plugin，使 `*IT` 在 integration-test 阶段运行、afterwards 必须关闭测试进程；surefire 不扫描 `*IT`。

- [ ] **Step 2: 运行确认当前 JAR 缺少前端**

Run: `mvn -f summer-admin/pom.xml clean verify`  
Expected: package 已生成 JAR，随后 ProductionJarIT 在 integration-test 阶段因静态 index 缺失而 FAIL，而不是因 JAR 尚未生成失败。

- [ ] **Step 3: 配置可复现前端 Maven 构建**

使用 frontend-maven-plugin 固定 Node 24 和 pnpm 10，执行 `pnpm install --frozen-lockfile`、`pnpm build:prod`；resources 插件把 dist 复制到 `target/classes/static`。配置 summer-pack 的 startClass；提供 `-Dskip.frontend` 仅用于调试，最终验收不使用。

- [ ] **Step 4: 构建并验证真实可执行 JAR**

Run:

```bash
mvn -f summer-admin/pom.xml clean verify
java -jar summer-admin/target/summer-admin-1.0.0-boot.jar
```

Expected: 构建及 ProductionJarIT PASS；手工启动的进程使用测试环境变量连接 `summer_admin_test`，首页、静态资源和健康 API 均可用，Ctrl+C 后进程正常结束。

- [ ] **Step 5: 执行核心端到端场景**

`CoreAdminE2eIT` 和内置浏览器依次验证：admin/user 登录；验证码错误；核心 CRUD；菜单/按钮/API 权限；五种数据范围及按 ID 越权；角色修改后重新登录生效；多标签与主题；操作/登录日志；生产 hash 页面刷新；响应和日志无密码。E2E 只操作测试库，并由 failsafe 在 package 后启动、在 integration-test 后清理应用。

- [ ] **Step 6: Commit**

```bash
git add summer-admin
git commit -m "feat: package and verify summer-admin single jar"
```

### Task 17: 完成文档、残留检查与全量最终回归

**Files:**
- Create: `summer-admin/README.md`
- Create: `summer-admin/NOTICE`（仅复制参考代码/素材时必须包含对应 MIT 文本）
- Modify: `docs/README.md`
- Modify: `docs/开发文档/{architecture.md,roadmap.md,tech-stack.md}`
- Modify: `docs/使用文档/` 中实际引用 sample 的文件
- Modify: `.claude/skills/deploy.md`
- Modify: `docs/test/` 中需要标明历史路径的报告
- Review: `docs/superpowers/specs/2026-11-07-summer-admin-design.md`
- Review: `docs/superpowers/plans/2026-11-07-summer-admin.md`

**Interfaces:**
- Consumes: 前 16 个任务的实际命令、配置和已验证行为。
- Produces: 使用者可独立复现的 README、无失效 sample 引用的当前文档、最终验证证据和明确的未验证项。

- [ ] **Step 1: 编写独立使用文档**

README 写明 JDK 25、Node 24、pnpm 10、MySQL 8、SQL 初始化、环境变量占位符、默认本地账号、开发/测试/生产命令、单 JAR、功能边界、JWT/验证码限制和参考许可。不得写入部署记录中的真实密码。

- [ ] **Step 2: 清理文档与仓库残留**

逐项处理 `summer-sample`、`cn.jiebaba.summer.sample`、`summer-example` 引用：build-test 内部测试包名可保留；当前使用示例指向 summer-admin；历史报告标注历史。更新发布验证的 JAR 路径，检查根 AGENTS 模块数量、依赖方向和 build-test 描述一致。

- [ ] **Step 3: 检查生成物、秘密和编码**

Run: `git status --short`，并使用 Grep 检查密码、JWT secret、真实部署凭据和失效路径。确认 target/dist/node_modules 被忽略，无意外日志/测试输出进入提交；所有新增文本 UTF-8 无 BOM、LF、末尾单换行，无行尾空格。

- [ ] **Step 4: 执行最终标准回归**

Run:

```bash
export JAVA_HOME="D:\jdk\jdk-25.0.4" && export PATH="/d/jdk/jdk-25.0.4/bin:$PATH"
mvn clean package
mvn -f summer-admin/pom.xml clean verify
cd summer-admin/frontend && pnpm test && pnpm lint && pnpm build:prod
```

Expected: 根 Maven 构建、admin verify（含 ProductionJarIT 与 CoreAdminE2eIT）和前端检查均成功。外部数据库历史测试若仍存在基线环境问题，明确列出，不声称全绿。

- [ ] **Step 5: 对照设计逐节检查遗漏和错误**

逐项核对设计 §2 的功能、§3 的迁移、§6 的 15 表、§8 的认证边界、§9 的五种数据范围、§12 的页面、§13 的单 JAR、§14 的测试；检查计划中类名、路径、权限标识和 DTO 与实际实现一致。任何偏差都更新实现或经用户确认后更新设计，不擅自缩小交付。

- [ ] **Step 6: Commit**

```bash
git add AGENTS.md build-test docs .claude summer-admin
git commit -m "docs: document and finalize summer-admin"
```
