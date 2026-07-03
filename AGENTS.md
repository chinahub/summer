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

## 校验与清理

- 提交前确认文件无 BOM、行尾为 LF、以单个换行结尾。
- 误存 BOM 时剥离前导 `EF BB BF`：Python 以二进制读、去掉开头 `b'\xef\xbb\xbf'` 再写回。
- 工具链层强制（仓库应具备）：
  - `.gitattributes`：`* text=auto eol=lf`；对 `.java/.xml/.yml/.yaml/.json/.md/.properties` 加 `working-tree-encoding=UTF-8`；`*.bat text eol=crlf`、`*.sh text eol=lf`。
  - `.editorconfig`：`charset = utf-8`、`end_of_line = lf`、`insert_final_newline = true`、`trim_trailing_whitespace = true`。
- 开发者本机：`git config core.autocrlf input`；IDE（IntelliJ）关闭“Create UTF-8 files with BOM”，行尾设为 LF。
