# build-test 模块约定

> **按需加载**：仅当 AI agent 读取 `build-test/` 目录下的文件时才会加载本文件。

## 模块职责

集中式测试模块：AOP 单测 + 集成测试 + summer-sample 冒烟测试。

## 模块约定

- **测试框架**：JUnit 5（Jupiter）
- **依赖**：依赖 summer-core（含测试微框架 `core.test` 包）和 summer-sample
- **不发布**：仅用于构建过程验证，不发布到 Maven Central
- **测试范围**：AOP 代理行为、IoC 容器、Web 路由、数据访问等核心功能
