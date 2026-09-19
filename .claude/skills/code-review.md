# 代码审查工作流

> **触发条件**：用户请求代码审查、PR review、检查代码质量时自动加载。
> **手动调用**：`/code-review`

## 审查流程

### 1. 获取变更范围
```bash
# 获取当前分支相对于 master 的变更
git diff master --name-only
# 或查看最近 N 个 commit 的变更
git log --oneline -5
```

### 2. 逐文件审查
对每个变更文件，检查以下维度：

- **编码规范**：UTF-8 无 BOM、LF 行尾、行尾无多余空格
- **注释规范**：超 20 行方法必须有中文 JavaDoc 注释；注释使用中文
- **命名规范**：类名 PascalCase、方法名 camelCase、包名全小写
- **零依赖原则**：summer-boot（含原 core/web/data/security/office）/summer-ai/summer-support 禁止引入新的第三方依赖
- **架构一致性**：新代码是否遵循模块职责划分（core→web→data→security→ai→office→boot 依赖链）
- **异常处理**：是否正确处理异常，避免吞异常
- **线程安全**：虚拟线程环境下的并发安全

### 3. 输出审查报告

```
## 代码审查报告
- 审查范围：[分支/commit 范围]
- 变更文件数：N
- 严重问题：X 个
- 建议改进：Y 个

### 严重问题
1. [文件:行号] 问题描述 → 修复建议

### 建议改进
1. [文件:行号] 建议描述
```

### 4. 验证
- 确认修复后运行 `mvn clean package -DskipTests` 验证编译通过
