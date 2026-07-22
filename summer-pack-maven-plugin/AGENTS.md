# summer-pack-maven-plugin 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-pack-maven-plugin/` 目录下的文件时才会加载本文件。

## 模块职责

Maven 打包插件：`mvn package` 时自动将应用产出为 BOOT-INF 结构的可执行 jar。

## 模块约定

- **目标**：`package` 生命周期绑定，生成可执行 jar（内含 JarLauncher + BOOT-INF 结构）
- **排除发布**：本模块不发布到 Maven Central
- **依赖**：仅 summer-boot-loader（用于内置 JarLauncher）
