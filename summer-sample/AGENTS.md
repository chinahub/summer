# summer-sample 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-sample/` 目录下的文件时才会加载本文件。

## 模块职责

示例应用：展示 summer 框架的完整使用方式，包含 controller/service/repository/aspect 示例。

## 模块约定

- **启动类**：使用 `@SummerBootApplication` 注解的主类
- **配置**：`application.yml` 位于 `src/main/resources/`
- **示例覆盖**：需覆盖所有核心功能的完整示例（Web 路由、ORM、安全、AI 对话等）
- **冒烟测试**：build-test 模块依赖本模块进行集成测试
