# 发布到 Maven Central 工作流

> **触发条件**：用户请求发布、deploy、release 时自动加载。
> **手动调用**：`/release`

## 前置条件
- 本地 GPG 密钥已配置（公钥已上传到 keyserver）
- `~/.m2/settings.xml` 中已配置 OSSRH 认证（server id: `ossrh`）
- 当前在 `master` 分支，工作区干净

## 发布流程

### 1. 版本确认
检查 `pom.xml` 中的 `<version>` 是否为要发布的版本号。

### 2. 运行测试
```bash
mvn clean package
```
确认全部测试通过后才能继续。

### 3. 执行发布
```bash
mvn clean deploy -P release
```

`release` profile 会自动：
- 生成 javadoc 和 sources jar
- 使用 GPG 签名所有构件
- 上传到 OSSRH（Maven Central 的 staging 仓库）

### 4. 验证发布
登录 https://s01.oss.sonatype.org/ 确认构件已上传到 staging 仓库。

### 5. 关闭并发布
在 OSSRH 控制台：Close → 等待验证通过 → Release。

## 注意事项
- `build-test` 和 `summer-pack-maven-plugin` 模块不发布到 Maven Central
- 发布完成后 Git 仓库打 tag：`git tag v{version} && git push origin v{version}`
