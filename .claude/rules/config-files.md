---
paths: ["**.xml", "**.yml", "**.yaml", "**.json", "**.properties"]
---

# 配置文件规则

## 编码
- 所有配置文件必须是 UTF-8 无 BOM 编码，LF 行尾
- 文件末尾恰好一个换行符

## Maven POM 约定
- 所有模块版本使用 `${summer.version}` 属性统一管理
- 插件版本在父 POM 的 `<properties>` 中集中声明
- 第三方依赖版本在父 POM 的 `<dependencyManagement>` 中声明
- 不在子模块中覆盖父 POM 的版本号

## YAML 配置约定
- 使用 2 空格缩进
- 敏感信息（密码、密钥）使用环境变量占位符，不硬编码
