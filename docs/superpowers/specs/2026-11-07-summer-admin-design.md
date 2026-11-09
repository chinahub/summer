# summer-admin 核心后台设计说明

日期：2026-11-07  
状态：已批准（第一阶段已按本设计完成交付）

## 1. 背景与目标

`summer-admin` 是 summer-boot 的独立、完整、可运行示例工程。它参考 RuoYi-Vue-Plus 的核心后台业务，以及 plus-ui `6.X-React` 分支的 React + Ant Design 页面和交互，但不复制其 Spring/MyBatis/Sa-Token 技术实现，也不追求完整复刻所有扩展模块。

第一阶段要同时证明两件事：

- summer-boot 能支撑真实的数据库后台应用，而不只是简单 Controller 示例。
- `summer-admin` 可以脱离 summer 根 Maven 聚合，独立安装依赖、构建、测试和运行。

参考来源：

- RuoYi-Vue-Plus：`https://gitee.com/dromara/RuoYi-Vue-Plus`
- plus-ui React 分支：`https://gitee.com/JavaLionLi/plus-ui/tree/6.X-React`

## 2. 已确认的范围

### 2.1 第一阶段包含

- 登录、退出、图形验证码、首页、个人中心。
- 用户、角色、菜单、部门、岗位、字典类型和字典数据管理。
- 系统参数、通知公告、操作日志、登录日志。
- 数据库用户认证、BCrypt、JWT、动态菜单、按钮权限和接口权限。
- 部门数据权限：全部、本部门、本部门及以下、仅本人、自定义部门。
- React 管理布局：侧边菜单、菜单折叠、多标签页、主题色、明暗模式、布局设置、全屏、统一查询表单和 CRUD 弹窗。
- MySQL 8 初始化脚本、真实接口测试和浏览器端到端验收。
- 开发时前后端分离；生产时前端打入后端 JAR，单进程提供页面和 API。

### 2.2 明确不包含

- 多租户、Redis、集群会话、在线用户和服务端令牌踢出。
- 工作流、代码生成、定时任务管理、服务监控、缓存监控。
- OSS/文件管理、头像上传、Excel 导入导出。
- 自助注册、短信登录、第三方登录、国际化。
- 通用 SQL 解析和透明改写、多数据源、异构数据库支持。
- 完整复制 RuoYi-Vue-Plus 的模块结构和接口。

这些边界不是后续必须依次实现的路线，只是第一阶段验收不包含它们。

## 3. 原仓库调整

当前仓库实际模块名为 `summer-sample`，不存在 `summer-example`。

### 3.1 删除与迁移

- 从根 `pom.xml` 的 `<modules>` 删除 `summer-sample`，最后删除该目录。
- 将 sample 的测试宿主、Controller、实体、Mapper、Service、配置和相关测试资源迁入 `build-test`，保持原冒烟测试需要的路由和行为。
- 合并配置文件时保留 build-test 已有配置，不直接覆盖。
- 删除 `build-test/pom.xml` 对 `summer-sample` 的依赖。
- sample 的包名可暂时保持 `cn.jiebaba.summer.sample`，作为 build-test 内部测试夹具，以减少无关重命名；新代码不得依赖这些夹具。
- 不保留空的 sample 目录，不修改 Maven Central 已发布的历史构件。

### 3.2 同步维护

- 更新根 `AGENTS.md`、`build-test/AGENTS.md` 的模块数量、依赖和职责描述。
- 更新 POM 注释、架构文档、安装/使用文档、测试文档和发布流程中失效的 sample 路径。
- 框架测试说明指向 build-test 内部测试夹具；面向使用者的完整示例指向 `summer-admin`。
- 不为批量替换而改写仍有效的技术内容；历史测试报告保留其历史性质，但标明 sample 已迁移，避免被当作当前执行路径。
- `.github/workflows/codeql.yml` 不依赖 sample，无需为了本任务改动。

## 4. 独立工程结构

工程位置：`E:\summer_workspace\summer-admin`

```text
summer-admin/
├── pom.xml
├── frontend/
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── config/
│   └── src/
├── src/
│   ├── main/java/cn/jiebaba/summer/admin/
│   ├── main/resources/
│   └── test/java/cn/jiebaba/summer/admin/
├── sql/
│   └── summer_admin.sql
├── .gitignore
└── README.md
```

后端根目录不建立 Node 工程，Node 清单一律放在 `frontend/`。

- Maven GAV：`cn.jiebaba.summer:summer-admin:1.0.0`。
- 主类：`cn.jiebaba.summer.admin.SummerAdminApplication`。
- Java 25；依赖 `cn.jiebaba.summer:summer-boot:4.2.0` 和 `summer-pack-maven-plugin:4.2.0`。
- 位于当前 summer 的同一个 Git 仓库中，但不初始化嵌套 Git 仓库，不单独设置发布标签。
- 不继承 summer-parent，不加入根 POM，不使用 `systemPath` 或相对路径依赖框架模块；根 `mvn clean package` 明确不构建 summer-admin。
- 第一阶段不新增 CI 或修改 `build.ps1`；summer-admin 的独立构建和验收命令写入自身 README。
- MySQL 驱动使用 `com.mysql:mysql-connector-j:8.4.0`，仅属于应用依赖。
- 后端根目录只维护一个 Maven 工程，不再拆 common/system 等多个 Maven 子模块；Java 包按业务职责划分。
- 所有文本遵守 UTF-8 无 BOM、LF、末尾单换行；Java 方法注释遵守仓库规范。

## 5. 框架使用与修改边界

第一阶段不修改 summer-boot 运行时代码。已确认现有能力可以支撑本工程：

- Web 路由、参数校验、静态资源、过滤器和异常处理。
- `BaseMapper`、Wrapper、分页、`@Transactional`。
- `UserDetailsService`、`AuthenticationManager`、BCrypt、JWT、`@PreAuthorize`。
- AOP 可用于操作日志。

这样可直接使用 Maven Central 的 4.2.0，避免本地修改同版本框架后独立工程不可复现。用户已允许补通用框架能力，但本设计没有发现必须为此破坏发布边界的缺口。

生产前端采用 hash history，例如 `/#/system/user`。静态服务只需返回 `index.html` 和资源文件，不需要增加 SPA history fallback。开发代理和生产同源路径都使用 `/api`，未知 API 不返回前端页面。

## 6. 数据库设计

### 6.1 初始化与配置

- 默认数据库名：`summer_admin`；字符集 `utf8mb4`，排序规则采用 MySQL 8 的 `utf8mb4_0900_ai_ci`。
- `sql/summer_admin.sql` 包含建库、建表、约束、索引和基础数据，可重复执行且不覆盖已有业务修改。
- 初始化脚本由使用者显式执行，应用启动不自动删表、重建或全量覆盖数据。
- 使用本机 Windows 可访问的 `localhost:3306` MySQL 8.0.46；不引入新的 Docker 编排。
- 应用配置通过 `SUMMER_DATASOURCE_USERNAME`、`SUMMER_DATASOURCE_PASSWORD`、`SUMMER_SECURITY_JWT_SECRET` 等环境变量注入。
- README 只写变量名和示例占位符，不复制部署记录中的真实凭据，不提交本地密码配置。
- 默认端口 8080；JWT 密钥至少 32 字节，生产必须显式配置稳定密钥。

### 6.2 表清单

| 表 | 用途 |
| --- | --- |
| `sys_user` | 用户、部门归属、状态、密码和基础资料 |
| `sys_dept` | 部门树、父节点、祖级路径、状态和排序 |
| `sys_post` | 岗位 |
| `sys_user_post` | 用户与岗位多对多关系 |
| `sys_role` | 角色、权限字符、数据范围和状态 |
| `sys_user_role` | 用户与角色多对多关系 |
| `sys_menu` | 目录、菜单、按钮、路由、组件和权限标识 |
| `sys_role_menu` | 角色与菜单关系 |
| `sys_role_dept` | 自定义数据范围的部门集合 |
| `sys_dict_type` | 字典类型 |
| `sys_dict_data` | 字典项、标签、值、样式和排序 |
| `sys_config` | 系统参数 |
| `sys_notice` | 通知公告及发布状态 |
| `sys_oper_log` | 后台操作日志 |
| `sys_login_log` | 登录成功和失败日志 |

核心实体使用 `BIGINT` 主键、排序、状态、审计时间和必要的逻辑删除字段。关系表使用物理删除。用户与角色/岗位、角色与菜单/部门的变更在同一事务内完成。唯一约束覆盖登录名、角色权限字符、字典类型和参数键等自然唯一项。

初始化至少提供：

- 公司/部门树、岗位、管理员角色和普通角色。
- 全部已交付页面的目录、菜单、按钮权限标识。
- 常用字典、示例参数和公告。
- `admin/admin123` 和 `user/user123`，密码在 SQL 中保存 BCrypt 哈希而非明文；文档明确这些只用于本地首次登录。

## 7. ORM 与事务

- 使用 summer 自研 `BaseMapper`、`LambdaQueryWrapper`/`QueryWrapper` 和 `Page`，不引入 MyBatis。
- 单表 CRUD 走 BaseMapper；多对多关系由独立关系 Mapper 和 Service 组合处理。
- 列表组装角色、岗位和部门名称时使用批量查询后在内存建立映射，避免逐行 N+1 查询。
- 复杂新增/修改/删除在 Service 层使用 `@Transactional`，并处理关系清理、唯一性冲突和并发影响行数。
- 不实现任意 SQL 注解或 XML Mapper；确实超出 Wrapper 能力的查询应先评估现有 `SqlExecutor`，而不是绕过预编译参数拼接 SQL。

## 8. 认证与授权

### 8.1 登录

公开端点：

- `GET /api/auth/captcha`：返回验证码开关、UUID 和 Base64 图片。
- `POST /api/auth/login`：接收 username、password、code、uuid。

需要有效令牌的端点：

- `POST /api/auth/logout`：记录已认证用户的退出操作；JWT 不在服务端撤销，前端必须清除令牌。令牌缺失或过期时接口可返回 401，前端仍必须清除本地令牌并返回登录页。

登录控制器先校验一次性验证码，再通过 `AuthenticationManager` 和数据库 `UserDetailsService` 验证用户，最后使用 `JwtEncoder` 签发访问令牌。自定义 `SecurityFilterChain` 只配置 JWT 解码、认证和授权，不启用内置登录过滤器，避免与自定义登录控制器冲突。

- 验证码使用 JDK 图片 API 生成，内存保存 UUID、摘要和过期时间，默认两分钟有效、验证后立即失效。
- 内存验证码明确表示只支持单实例示例，不伪装成集群方案。
- 默认只签访问令牌，不接 refresh token；有效期默认两小时，可配置。
- 登录成功、凭证失败、验证码失败和账号停用均写登录日志，但不泄露密码或验证码。
- Bearer Token 放在 Authorization 头，不使用 Cookie，因此本阶段不启用 CSRF。

### 8.2 权限

- 按 RuoYi 惯例，固定 `user_id=1` 为超级管理员标记；初始化 SQL 必须保证 admin 使用该 ID。
- `UserDetailsService` 将角色和菜单权限标识加载为 authorities；超级管理员加载全部有效菜单权限，普通用户只拥有启用角色对应菜单的并集，不依赖框架识别通配符权限。
- 超级管理员在动态菜单和数据范围计算中绕过限制；保护内置管理员及危险操作也以 `user_id=1` 和当前用户 ID 判断。
- 后端使用 `@PreAuthorize(authorities=...)` 或框架对应声明式属性保护每个管理接口，不能只依赖前端隐藏按钮。
- 动态路由接口只返回当前用户可见目录和菜单；按钮不进入页面路由，但权限标识返回前端。
- 用户、角色、菜单发生变化后，已签发 JWT 中的权限不会自动刷新；前端提示重新登录，重新登录后生效。令牌到期前停用账号也不会被服务端强制踢出，这是第一阶段接受的无状态边界。
- 删除或禁用 `user_id=1`、删除当前登录用户等危险操作必须被服务端拒绝。

## 9. 部门数据权限

数据范围定义在角色上，使用 `ALL`、`DEPT`、`DEPT_AND_CHILDREN`、`SELF`、`CUSTOM` 五种稳定枚举值。

第一阶段将数据权限真实应用于用户列表、用户详情、用户修改/删除以及可见部门树，防止只过滤列表而仍可通过 ID 越权操作。其他没有部门归属的系统表不虚构数据范围。

实现方式：

- `DataScopeResolver` 根据当前用户所有启用角色求权限并集，返回无限制或允许部门集合；`SELF` 对用户表转换为用户 ID 限制。
- 子部门通过部门 `ancestors` 路径计算；部门变更时同步维护后代路径。
- 查询 Service 将解析结果显式加入 Wrapper；按 ID 操作先加载目标并校验范围。
- 多角色按可访问范围并集处理，任一 `ALL` 即无限制；不能错误取交集。
- 无权限时生成永假条件，而不是省略条件导致全表泄露。
- 不在第一阶段实现 SQL 解析器或透明拦截器；未来业务模块可复用 Resolver，但必须显式声明实体如何映射部门/本人字段。

## 10. 业务行为

### 10.1 通用 CRUD

- 列表支持必要的条件查询、分页和排序；详情、新增、修改、删除返回统一结构。
- DTO 与数据库实体分离，密码、BCrypt 哈希、关系字段和内部审计字段不直接暴露。
- Bean Validation 校验必填、长度、邮箱等；Service 再校验唯一性、引用关系、状态和越权。
- 删除前校验子节点和业务引用；例如有子部门/用户的部门、有子菜单的菜单不能直接删除。
- 个人中心支持查看和修改昵称、邮箱、手机号、性别等基础资料，以及校验原密码后修改密码。

### 10.2 角色与菜单

- 用户可分配角色和岗位；角色可分配菜单和数据范围；自定义范围可分配部门。
- 菜单类型区分目录、菜单、按钮，维护名称、路径、组件、图标、权限标识、排序、可见和状态。
- 前端组件字段只能映射到预注册页面白名单，不允许后端返回任意模块路径让前端动态加载。
- 角色菜单树只返回可用菜单；停用角色不参与授权。

### 10.3 字典、参数和公告

- 字典类型与字典数据分两个页面或主从页面管理，前端字典 Hook 按类型获取启用项。
- 参数管理提供 CRUD；第一阶段参数直接读数据库，不引入分布式配置或刷新广播。
- 公告支持标题、类型、内容、状态和排序，列表只展示当前用户有权查看的数据。

### 10.4 日志

- 通过 admin 自定义注解和 AOP 标记需要记录的管理操作。
- 操作日志保存模块、动作、操作人、方法、URL、IP、耗时、请求结果和异常摘要；敏感字段在序列化前剔除。
- 日志记录失败不覆盖原业务结果；保存过程不得再次触发操作日志。
- 登录日志记录用户名、IP、User-Agent、成功状态、消息和时间。
- 两类日志支持条件分页查询、详情、删除和清空；清空动作本身也记录操作日志。

## 11. API 约定

所有业务 API 使用 `/api` 前缀，前端不直接依赖 RuoYi 的 Sa-Token 响应格式。

通用响应：

```json
{"code":200,"msg":"操作成功","data":{}}
```

分页响应：

```json
{"code":200,"msg":"查询成功","rows":[],"total":0}
```

- `code` 与 HTTP 状态语义一致；失败使用 400、401、403、404、409 或 500，并返回可供页面显示的安全消息。
- 登录成功 `data` 至少包含 `access_token`、`token_type`、`expires_in`。
- 认证后提供当前用户资料、角色、权限集合和动态路由端点。
- REST 路径按 `/api/system/users`、`roles`、`menus`、`depts`、`posts`、`dict/types`、`dict/data`、`configs`、`notices`、`logs/operations`、`logs/logins` 组织。
- 列表分页参数统一为 `pageNum`、`pageSize`；具体查询字段由各模块 DTO 定义。
- 后端统一异常处理把校验错误、唯一冲突、认证失败和业务异常转换为上述格式，不把堆栈返回浏览器。

## 12. 前端设计

### 12.1 技术结构

- React 19、TypeScript、Ant Design 6、Ant Design Pro Components、Umi Max、Zustand、TanStack Query、Axios。
- Node 24 LTS、pnpm 10；版本以提交的可复现 lockfile 为准。
- 目录划分为 api、components、layouts、pages、stores、types、utils。
- 登录、用户、权限、标签页和布局状态职责分离；服务端列表优先由 TanStack Query 管理，避免重复全局缓存。

### 12.2 页面和交互

- 登录页保留 plus-ui 的品牌区、登录卡片、验证码和错误反馈，但移除注册及第三方登录。
- BasicLayout 保留品牌、动态侧边菜单、折叠、用户菜单、多标签页、刷新/关闭标签、主题色、明暗模式、侧栏/顶部/混合布局、全屏。
- 首页展示真实的用户、角色、部门、公告等汇总，以及 summer 技术栈说明，不伪造监控指标。
- CRUD 页面使用 ProTable/查询表单、表格、 Modal/Drawer 表单、删除确认和统一错误提示。
- 用户页包含部门树过滤、角色/岗位分配和重置密码；角色页包含菜单树及数据范围；菜单/部门使用树形展示。
- 权限 Hook/组件同时控制菜单、按钮和路由；无权限路由显示 403，未知路由显示 404。
- 所有表单有加载、校验、重复提交防护和失败反馈；空列表、请求失败、令牌过期均有明确状态。
- 第一阶段使用简体中文和 Ant Design zh_CN locale，不显示无实际作用的语言切换入口。

### 12.3 参考代码与许可

优先按参考项目的视觉效果和交互重写。若复制任何代码、图片或其他素材，保留其 MIT 许可与版权声明，并在工程中增加相应 NOTICE；不得移除上游许可。

## 13. 构建与运行

### 13.1 开发模式

- 后端：`SummerAdminApplication` 或 Maven 启动，端口 8080。
- 前端：`pnpm dev`，端口 8000，`/api` 代理到 `http://localhost:8080`。
- 后端 CORS 只开放开发前端来源；生产同源不依赖宽松 CORS。

### 13.2 生产单 JAR

- `frontend-maven-plugin` 只作为构建工具，按固定 Node/pnpm 版本执行 `pnpm install --frozen-lockfile` 和 `pnpm build:prod`。
- Maven 将 `frontend/dist` 复制到 `target/classes/static`，生成资源只进入 target，不写回 src。
- summer-pack-maven-plugin 生成可执行 JAR。
- 默认 `mvn clean package` 完成前后端构建；提供显式跳过前端构建的构建参数供纯后端调试，但发布验收不得跳过。
- `java -jar` 后访问 `/` 和 `/#/...` 页面，API 使用同源 `/api`。

## 14. 测试与验收

### 14.1 原框架回归

- 使用 JDK 25 执行根工程标准 `mvn clean package`。
- 确认 sample 删除后无编译引用，build-test 内原 Web、ORM、数据库、WebSocket、KeepAlive、TLS 等测试仍可运行。
- 对确需外部数据库且当前环境不满足的历史测试，不伪造通过；区分本任务回归与既有环境问题。

### 14.2 summer-admin 自动化验证

- 后端运行单元测试、Service 事务/权限测试和真实 HTTP 接口测试。
- 测试使用独立 `summer_admin_test` 数据库和独立端口，不清空开发数据库。
- 覆盖验证码一次性、登录成功/失败、无令牌 401、无权限 403、用户/角色/菜单 CRUD、关系事务回滚、五种数据范围及按 ID 越权。
- 覆盖字典、参数、公告和日志基本链路，检查密码及敏感请求字段不出现在响应和日志中。
- 前端执行 Umi setup、TypeScript 检查、lint 和生产构建。
- 最终对实际可执行 JAR 做接口与静态资源检查，而不只验证 import 或编译。

### 14.3 浏览器端到端场景

- admin 登录、首页、动态菜单和核心 CRUD。
- user 登录后只看到授权菜单和按钮，直接访问无权限 API 返回 403。
- 不同数据范围用户看到不同部门/用户集合，按 ID 越权同样被拒绝。
- 新增角色并分配菜单后重新登录，菜单和按钮权限正确变化。
- 多标签页打开/刷新/关闭、主题和布局切换、生产 hash 路由刷新。
- 操作日志和登录日志出现对应记录，响应与日志无密码泄露。

## 15. 文档交付

`summer-admin/README.md` 至少包含：

- 技术栈、功能边界和目录说明。
- JDK 25、Node 24、pnpm 10、MySQL 8 前置条件。
- 初始化 SQL、环境变量、默认本地账号和首次登录后改密提示。
- 后端开发、前端开发、测试、生产构建和单 JAR 启动命令。
- 单实例验证码、无状态 JWT 权限刷新和不做服务端踢出的限制。
- 与 RuoYi-Vue-Plus/plus-ui 的关系及许可说明。

根文档同时更新示例入口。设计审批后再编写分阶段开发计划，计划完成后另做遗漏与错误检查。

## 16. 设计者已作出的实现决定

以下不是用户逐条指定的决定，而是为满足目标作出的工程取舍，审批设计时可一并调整：

- 独立项目版本使用 1.0.0，框架继续使用已发布的 4.2.0。
- 第一阶段不改 summer-boot 运行时代码，前端使用 hash history。
- 不使用 refresh token，权限变更通过重新登录生效。
- 数据权限采用显式查询条件，不做透明 SQL 改写。
- 操作日志采用 admin 内 AOP；验证码采用单实例内存存储。
- 前端中文单语言，不包含无实际后端能力的按钮和菜单。
- sample 代码作为 build-test 内部测试夹具保留，而不是变成 summer-admin 的依赖。
