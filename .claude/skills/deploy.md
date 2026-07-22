# 部署工作流

> **触发条件**：用户请求部署、deploy、打包部署时自动加载。
> **手动调用**：`/deploy`

## 部署流程

### 1. 构建可执行 jar
```bash
mvn clean package -DskipTests
```
在 `summer-sample/target/`（或你的应用模块）下会生成可执行 jar。

### 2. 验证 jar 结构
```bash
jar tf summer-sample/target/*.jar | head -20
```
确认包含：
- `BOOT-INF/classes/` —— 应用类
- `BOOT-INF/lib/` —— 依赖 jar
- `cn/jiebaba/summer/boot/loader/` —— JarLauncher

### 3. 本地运行验证
```bash
java -jar summer-sample/target/summer-sample-*.jar
```
确认应用启动成功，端口正常监听（默认 8080）。

### 4. 健康检查
```bash
curl http://localhost:8080/health  # 或其他自定义端点
```

### 5. 部署到服务器
将 jar 和 `application.yml` 复制到服务器，使用 systemd 或 nohup 启动：
```bash
nohup java -jar summer-app.jar > app.log 2>&1 &
```

## 配置文件
- 生产配置建议使用外部 `application.yml`（放在 jar 同目录或通过 `--spring.config.location` 指定）
- 敏感信息（数据库密码等）建议使用环境变量：`${DB_PASSWORD}`
