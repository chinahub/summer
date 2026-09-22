# summer-support 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-support/` 目录下的文件时才会加载本文件。

## 模块职责

OCR 文字识别支持模块（原 summer-office 的 OCR 部分独立而成）。DB（文本检测）→ 方向分类 → CRNN（文本识别）全流水线：图像处理为纯 JDK（`javax.imageio` + 手写像素/几何运算），神经网络推理通过 FFM 调用本地 `onnxruntime` 共享库完成。无需 JNI 胶水代码、无需 onnxruntime 的 Java 绑定包，原生库即唯一运行期外部依赖（类比 JDBC 驱动）。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summer.support.ocr` | OCR：Ocr/OcrConfig/OcrResult/OcrItem/OcrException（继承 core 的 SummerException）+ TextDetector（DB 检测）/TextClassifier（方向分类）/TextRecognizer（CRNN 识别）/DbPostProcess + OcrAutoConfiguration/OcrProperties（summer.ocr.* 配置绑定） |

## 模块约定

- **依赖方向**：本模块依赖 summer-core（使用其 onnx 引擎与注解/环境体系），不依赖 summer-boot；summer-boot 也不依赖本模块，由 `SummerApplication` 以 `isClassPresent("cn.jiebaba.summer.support.ocr.Ocr")` 探测后经 `Class.forName` 反射注册 `OcrAutoConfiguration`
- **异常体系**：`OcrException` 继承 summer-core 的 `cn.jiebaba.summer.core.SummerException`（summer 框架统一基础异常），不与 boot 的 `OfficeException` 关联
- **懒加载**：`Ocr` bean 以 `@Lazy` 注册，仅在注入时初始化原生引擎与模型；未配置 `summer.ocr.*` 且未注入时不影响启动
- **必要配置**：`summer.ocr.lib-path`（onnxruntime 原生库）、`summer.ocr.det-model-path`、`summer.ocr.rec-model-path`；`dict-path` 在使用内嵌字典的 PP-OCRv4/v6 模型时可选
