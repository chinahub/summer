# summer-boot-loader 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-boot-loader/` 目录下的文件时才会加载本文件。

## 模块职责

可执行 jar 启动器（`java -jar` 入口）。由 summer-pack-maven-plugin 打包时内置到 BOOT-INF 结构的 jar 中。

## 模块约定

- **入口类**：`JarLauncher`——解析 BOOT-INF 目录结构，加载类路径，启动主类
- **打包结构**：遵循 Spring Boot 风格的 BOOT-INF/classes + BOOT-INF/lib 布局
- **不依赖其他模块**：独立类加载器，只依赖 JDK
