# build-test 模块约定

> **按需加载**：仅当 AI agent 读取 `build-test/` 目录下的文件时才会加载本文件。

## 模块职责

集中式测试模块：AOP 单测 + 集成测试 + 内聚的 sample 测试夹具（原 summer-sample 模块已整体迁入，包名保留 `cn.jiebaba.summer.sample`）。

## 模块约定

- **测试框架**：JUnit 5（Jupiter）
- **依赖**：依赖 summer-boot（含测试微框架 `core.test` 包）、summer-ai、summer-support（OCR 冒烟）；不再依赖 summer-sample 构件（其源码已迁入本模块 src/main）
- **不发布**：仅用于构建过程验证，不发布到 Maven Central
- **测试范围**：AOP 代理行为、IoC 容器、Web 路由、数据访问等核心功能
- **夹具约定**：sample 夹具（Application/controller/service/mapper + application.yml）与 @Test 用例同位于 `src/main`，由 surefire 扫描主输出目录统一执行
