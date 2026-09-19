---
paths: ["summer-boot/**", "summer-support/**", "summer-ai/**"]
---

# 核心模块依赖规则

## 零第三方依赖原则

以下核心模块禁止引入任何第三方运行时依赖（SLF4J API / JUnit API 除外）：
- **summer-boot** —— 框架核心与运行时（原 summer-core/summer-web/summer-data/summer-security/summer-office 已并入本模块）
- **summer-ai** —— AI 对话抽象（纯 JDK HTTP 客户端）
- **summer-support** —— OCR 文字识别（FFM 调用 onnxruntime 原生库，无 Java 绑定包）

## 依赖层次

```
summer-boot          （零 summer-* 依赖：IoC/AOP/日志 + Web + ORM + 安全 + 文档读写）
  ↑
summer-ai            （只依赖 summer-boot）
summer-support       （只依赖 summer-boot，OCR）
```

summer-boot 不编译期依赖 summer-ai / summer-support（否则构成模块循环依赖）；
二者的自动配置由 SummerApplication 以 classpath 探测（`isClassPresent`）+ `Class.forName`
反射注册（AI 自动配置类在 summer-ai 模块内，包名仍为 `cn.jiebaba.summer.boot.ai`）。

## 例外
- SLF4J API / JUnit API：summer-boot 允许 optional 依赖（SLF4J→JUL 绑定与 @SummerTest/SummerExtension 测试微框架）
