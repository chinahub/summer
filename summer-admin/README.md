# summer-admin

基于 [summer-boot](https://github.com/) 4.2.0（自研 JDK 25 轻量级微服务框架）的独立管理后台示例，参考 RuoYi-Vue-Plus 的核心后台业务与 plus-ui `6.X-React` 分支的页面交互重写。

- 后端：Java 25 + summer-boot（嵌入式 HTTP / ORM / JWT 安全，零 Servlet），MySQL 8 持久化。
- 前端：React 19 + TypeScript + Ant Design 6 + Ant Design Pro Components + Umi Max + Zustand + TanStack Query。
- 独立工程：不继承 summer-parent、不加入根 Maven 聚合，`mvn clean package` 一步产出前后端一体的单可执行 JAR。

## 前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 | `maven.compiler.release=25` |
| Node.js | 24 | 仅前端开发需要；Maven 构建由 frontend-maven-plugin 自动安装固定版本 |
| pnpm | 10 | 同上；以 `frontend/pnpm-lock.yaml` 为准 |
| MySQL | 8 | 本机 `localhost:3306` 可访问，需有建库权限 |

> 本机 PATH 默认 java 为 JDK 17 时，先执行：
> `export JAVA_HOME="D:\jdk\jdk-25.0.4" && export PATH="/d/jdk/jdk-25.0.4/bin:$PATH"`

## 快速开始

1. 初始化数据库（可重复执行、幂等，只首次写入种子数据）：

   ```bash
   mysql -u root -p < sql/summer_admin.sql
   ```

   脚本创建 `summer_admin` 库（utf8mb4 / utf8mb4_0900_ai_ci）、15 张 `sys_*` 表和种子数据。

2. 注入环境变量（见下表），然后构建并运行：

   ```bash
   mvn clean package
   java -jar target/summer-admin-1.0.0-boot.jar
   ```

3. 浏览器访问 `http://localhost:8080/`，使用默认本地账号登录（首次登录后请立即改密）：

   | 账号 | 密码 | 角色 |
   | --- | --- | --- |
   | `admin` | `admin123` | 超级管理员（user_id=1） |
   | `user` | `user123` | 普通角色 |

## 环境变量

凭据不写入任何配置文件，全部经环境变量注入（summer 配置支持大写下划线环境变量到点分隔属性的松弛映射）：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `SUMMER_DATASOURCE_USERNAME` | 是 | MySQL 用户名 |
| `SUMMER_DATASOURCE_PASSWORD` | 是 | MySQL 密码 |
| `SUMMER_SECURITY_JWT_SECRET` | 生产必填 | JWT 签名密钥，至少 32 字节；未设置时框架生成临时密钥并告警，重启后旧令牌全部失效 |
| `SUMMER_ADMIN_CAPTCHA_ENABLED` | 否 | 图形验证码开关（`true`/`false`，默认 `true`） |

其他可调项见 `src/main/resources/application.yml`（数据源 URL、连接池、JWT 有效期默认 7200 秒、验证码有效期默认 120 秒）。

## 开发模式

前后端分离开发，前端 dev server 将 `/api` 代理到 `http://localhost:8080`（可用 `API_PROXY_TARGET` 覆盖）：

```bash
# 终端 1：后端（IDE 直接运行 cn.jiebaba.summer.admin.SummerAdminApplication，
# 或跳过前端构建后启动）
mvn clean package -Dskip.frontend=true
java -jar target/summer-admin-1.0.0-boot.jar

# 终端 2：前端（端口 8000）
cd frontend
pnpm install
pnpm dev
```

## 测试

需要本机 MySQL 8 可用，并设置 `SUMMER_DATASOURCE_USERNAME` / `SUMMER_DATASOURCE_PASSWORD`（测试会创建并重建独立的 `summer_admin_test` 库，不影响开发库 `summer_admin`）：

```bash
# 后端：单元测试 + 真实可执行 JAR 集成测试（ProductionJarIT、CoreAdminE2eIT，独立端口 18090）
mvn clean verify

# 前端
cd frontend
pnpm test        # vitest
pnpm lint        # max lint + tsc --noEmit
pnpm build:prod  # 生产构建
```

## 生产构建（单 JAR）

```bash
mvn clean package
java -jar target/summer-admin-1.0.0-boot.jar
```

Maven 构建链：frontend-maven-plugin 按固定 Node 24 / pnpm 10 版本执行 `pnpm install --frozen-lockfile` 与 `pnpm build:prod`，前端产物只复制进 `target/classes/static`（不写回 src），summer-pack-maven-plugin 产出 BOOT-INF 结构可执行 JAR，单进程同时提供页面（hash 路由，如 `/#/system/user`）与同源 `/api`。纯后端调试可 `-Dskip.frontend=true`，发布验收不得跳过。

## 功能边界

包含：登录 / 退出 / 图形验证码、首页汇总、个人中心（资料与改密）、用户 / 角色 / 菜单 / 部门 / 岗位 / 字典类型 / 字典数据 / 系统参数 / 通知公告 / 操作日志 / 登录日志管理；数据库用户认证、BCrypt、JWT、动态菜单、按钮与接口权限；部门数据权限五种范围（全部 / 本部门 / 本部门及以下 / 仅本人 / 自定义部门）。

明确不包含（第一阶段范围之外）：多租户、Redis、集群会话与在线用户、服务端令牌踢出；工作流、代码生成、定时任务管理、服务监控、缓存监控；OSS / 文件管理 / 头像上传 / Excel 导入导出；自助注册、短信登录、第三方登录、国际化；通用 SQL 解析改写、多数据源、异构数据库。

## 已知限制

- **验证码为单实例内存存储**：图形验证码保存在应用内存中，仅支持单实例部署，不伪装成集群方案。
- **JWT 无状态**：不签 refresh token（访问令牌默认两小时）；用户 / 角色 / 菜单变更后，已签发令牌内的权限不会自动刷新，需重新登录生效；到期前停用账号不会被服务端强制踢出。
- **Bearer Token 不使用 Cookie**，本阶段不启用 CSRF。
- **连接池启动预填充**：应用启动期即创建连接，数据库不可用时启动失败（快速失败，属预期行为）。

## 参考与许可

- 业务与交互参考 [RuoYi-Vue-Plus](https://gitee.com/dromara/RuoYi-Vue-Plus)（MIT）与 [plus-ui](https://gitee.com/JavaLionLi/plus-ui) `6.X-React` 分支（MIT）；本工程未复制其代码、图片或其他素材，技术实现（Web / ORM / 安全）基于 summer-boot 自研。
- 本工程代码基于 summer 框架（Apache License 2.0）。
