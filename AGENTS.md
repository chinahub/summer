# AGENTS.md

本文件是给 Codex（及所有兼容 AGENTS.md 规范的 coding agent）的仓库级工作约定。
Codex 启动时会自动把根目录与本目录向上至根的所有 `AGENTS.md` 纳入上下文，无需手动读取。
子目录可放置更具体的 `AGENTS.md` 覆盖上级规则（更深层优先）。

## 文本文件编码规范（强制）

创建或修改任何文本文件（`.java` `.xml` `.yml` `.yaml` `.json` `.properties` `.md` `.sh` `.bat` 等）时必须遵守：

- **编码：UTF-8，无 BOM**。禁止写入 UTF-8 with BOM（前导字节 `EF BB BF`）。
- **行尾：LF（`\n`）**。禁止 CRLF；Windows 上也以 LF 保存（`.bat` 等必须 CRLF 的除外）。
- **文件结尾：恰好一个换行符**。最后一行末尾保留单个 `\n`；禁止末尾多余空行或重复 BOM。
- **行内空白：禁止行尾多余空格**；缩进遵循各文件既有风格（Java 用 4 空格，Tab/空格不混用）。
- **shebang**：`#!/bin/sh` 等必须是文件第一个字节，前面不得有 BOM。

> 原因：UTF-8 字节序无关，BOM 无意义；BOM 会导致 shebang 失效、JSON/XML/YAML 解析失败、
> Java `illegal character: \uFEFF`、配置键名错误、diff 噪音与重复累积。

## 方法注释规范（强制）

针对 Java（`.java`）等源码文件中的方法，必须遵守：

- **超 20 行方法必须有注释**：单个方法体（含方法签名到结束大括号）超过 20 行时，必须在方法上方添加注释，说明该方法的用途、关键参数或返回值。
- **注释一律使用中文**：所有注释（方法注释、字段注释、行内注释、`//` 与 `/* */`、JavaDoc 等）必须用中文撰写；不得保留英文注释。
  - 既有英文注释须翻译为中文；专有名词、API 名称、标识符等可保留原文。
  - 代码示例、命令、配置键名、日志关键字等不属于注释文案，不在此限。
- **注释风格**：优先使用 JavaDoc（`/** ... */`）标注方法用途；行内注释用 `//` 且与代码同语言（中文）。
- **行内中文不产生 BOM**：遵循上文“文本文件编码规范”，UTF-8 无 BOM、LF 行尾。

> 原因：统一中文注释便于团队协作与评审；长方法缺乏注释会降低可维护性。

## 校验与清理

- 提交前确认文件无 BOM、行尾为 LF、以单个换行结尾。
- 误存 BOM 时剥离前导 `EF BB BF`：Python 以二进制读、去掉开头 `b'\xef\xbb\xbf'` 再写回。
- 工具链层强制（仓库应具备）：
  - `.gitattributes`：`* text=auto eol=lf`；对 `.java/.xml/.yml/.yaml/.json/.md/.properties` 加 `working-tree-encoding=UTF-8`；`*.bat text eol=crlf`、`*.sh text eol=lf`。
  - `.editorconfig`：`charset = utf-8`、`end_of_line = lf`、`insert_final_newline = true`、`trim_trailing_whitespace = true`。
- 开发者本机：`git config core.autocrlf input`；IDE（IntelliJ）关闭“Create UTF-8 files with BOM”，行尾设为 LF。
