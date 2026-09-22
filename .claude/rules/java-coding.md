---
paths: ["**.java"]
---

# Java 编码规则

## 文件编码
- 所有 `.java` 文件必须是 UTF-8 无 BOM 编码，LF 行尾
- 文件末尾恰好一个换行符
- 禁止行尾多余空格
- 缩进使用 4 个空格，禁止 Tab

## 注释规范
- 超过 20 行的方法必须在方法上方添加 JavaDoc 注释（`/** ... */`）
- 所有注释使用中文（专有名词、API 名称、标识符可保留原文）
- 既有英文注释需翻译为中文

## 依赖原则
- summer-core（`cn.jiebaba.summer.core.*` 包）/summer-boot/summer-ai/summer-support 禁止引入第三方依赖
- 新增依赖需在父 POM 的 `<dependencyManagement>` 中声明版本
