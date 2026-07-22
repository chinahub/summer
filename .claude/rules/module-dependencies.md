---
paths: ["summer-core/**", "summer-web/**", "summer-data/**", "summer-security/**", "summer-ai/**"]
---

# 核心模块依赖规则

## 零第三方依赖原则

以下核心模块禁止引入任何第三方依赖（SLF4J API 除外）：
- **summer-core** —— 框架核心，所有模块的基础
- **summer-web** —— 嵌入式 HTTP 服务器
- **summer-data** —— ORM 与数据访问
- **summer-security** —— 安全与认证
- **summer-ai** —— AI 对话抽象

## 依赖层次

```
summer-core          （零依赖）
  ↑
summer-web           （只依赖 core）
summer-data          （只依赖 core）
summer-security      （只依赖 core + web）
summer-ai            （只依赖 core）
summer-office        （只依赖 core，xlsx/docx/pdf 按 classpath 探测）
  ↑
summer-boot          （依赖所有功能模块）
```

## 例外
- summer-office：xlsx 需 Apache POI、docx 需 POI-OOXML、pdf 需 PDFBox——按 classpath 探测，未检测到时优雅降级
- SLF4J API：core 模块允许 optional 依赖
