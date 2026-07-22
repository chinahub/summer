# 模型与原生库下载指南

> summer 的本地 AI 能力（OCR、Embedding）基于 ONNX Runtime 推理，所需原生库与模型文件作为部署期外部资产按路径加载（类比 JDBC 驱动），不打包进 JAR。本页集中给出各项资产的获取来源与目录组织建议。

summer 通过 JDK 25 的 Foreign Function & Memory API（`java.lang.foreign`）直连 onnxruntime 原生库，无需 JNI 胶水、无需引入 onnxruntime 的 Java 绑定包。`OnnxEngine`（位于 summer-core，供 summer-ai 与 summer-office 共享）绑定 onnxruntime 1.16~1.20 的 C API（`ORT_API_VERSION=20`）。

## 资产总览

| 能力 | 资产 | 用途 |
| --- | --- | --- |
| 共享 | onnxruntime 原生库 | 神经网络推理引擎，OCR 与 Embedding 共用 |
| OCR | PP-OCR 模型 | 文本检测 / 方向分类 / 文本识别 |
| Embedding | BGE-M3 ONNX 模型 | 文本向量化（FP16） |
| Embedding | tokenizer.json | XLM-RoBERTa BPE 分词器 |

## onnxruntime 原生库

OCR 与本地 Embedding 共用同一个 onnxruntime 原生库，下载一次即可。

| 平台 | 文件 | 下载 |
| --- | --- | --- |
| Windows x64 | `onnxruntime.dll` | [onnxruntime-win-x64-1.20.1.zip](https://github.com/microsoft/onnxruntime/releases/tag/v1.20.1)（解压取 `lib/` 下 dll） |
| Linux x64 | `libonnxruntime.so` | [onnxruntime-linux-x64-1.20.1.tgz](https://github.com/microsoft/onnxruntime/releases/tag/v1.20.1)（解压取 `lib/` 下 so） |
| macOS | `libonnxruntime.dylib` | [onnxruntime-osx-1.20.1](https://github.com/microsoft/onnxruntime/releases/tag/v1.20.1) |

> 版本要求：1.16~1.20（`ORT_API_VERSION=20`），推荐 1.20.1，选 CPU 版即可。
> 下载页：https://github.com/microsoft/onnxruntime/releases

## OCR 模型（PP-OCRv6）

| 模型 | 文件 | 下载来源 |
| --- | --- | --- |
| 检测 det | `PP-OCRv6_det_small.onnx` | ModelScope [RapidAI/RapidOCR](https://modelscope.cn/models/RapidAI/RapidOCR)（tag `v3.9.1`）`onnx/PP-OCRv6/det/` |
| 识别 rec | `PP-OCRv6_rec_small.onnx` | 同上 `onnx/PP-OCRv6/rec/` |
| 分类 cls | `ch_ppocr_mobile_v2.0_cls_infer.onnx` | 同上（v6 复用 v4 cls） |

> PP-OCRv6 识别模型内嵌多语言字符表（约 18708 字符），无需单独字典文件。
> 旧版本（v3/v4）模型见 [RapidOcr-Java 模型目录](https://github.com/MyMonsterCat/RapidOcr-Java/tree/main/rapidocr-onnx-models/src/main/resources/models)。
> 也可用 `paddle2onnx` 从 [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) 推理模型自行转换。

详细用法见 [OCR 文档](ocr.md)。

## Embedding 模型（BGE-M3）

BGE-M3 基于 XLM-RoBERTa，输出 1024 维向量，支持多语言、最长 8192 token。summer 默认 CLS 池化 + L2 归一化（与 BGE 官方一致），输出可直接用于余弦相似度检索。

### 1. tokenizer.json

直接从官方仓库下载（XLM-RoBERTa BPE 分词器配置）：

- HuggingFace：[BAAI/bge-m3](https://huggingface.co/BAAI/bge-m3)（仓库根目录 `tokenizer.json`）
- ModelScope：[BAAI/bge-m3](https://modelscope.cn/models/BAAI/bge-m3)

### 2. ONNX 模型（FP16）

官方仓库仅提供 PyTorch 权重，ONNX 需转换或取社区版。

**方式 A：optimum 转换（推荐，精度可控）**

```bash
pip install "optimum[onnxruntime]" transformers
# 从 BAAI/bge-m3 导出 FP16 ONNX
optimum-cli export onnx --model BAAI/bge-m3 --fp16 ./bge-m3-onnx
```

导出产物 `bge-m3-onnx/model.onnx`（FP16）即所需模型文件，输入名为 `input_ids`/`attention_mask`，输出 `last_hidden_state`，与 `OnnxEmbeddingModel` 约定匹配。

**方式 B：社区现成 ONNX（备选）**

- HuggingFace：[Xenova/bge-m3](https://huggingface.co/Xenova/bge-m3)（`onnx/` 子目录，transformers.js 导出）

> 若社区版输入名不是 `input_ids`/`attention_mask`，需用 `optimum` 重新导出以匹配 `OnnxEmbeddingModel` 的输入约定。

### 配置示例

```properties
summer.ai.embedding.enabled=true
summer.ai.embedding.type=onnx
summer.ai.embedding.onnx.lib-path=D:/ai/onnxruntime.dll
summer.ai.embedding.onnx.model-path=D:/ai/bge-m3/model.onnx
summer.ai.embedding.onnx.tokenizer-path=D:/ai/bge-m3/tokenizer.json
summer.ai.embedding.onnx.max-seq-len=8192
summer.ai.embedding.onnx.pooling=cls
```

详细用法见 [AI 对话文档](ai.md)。

## 目录组织建议

```
D:/ai/                         # 或任意部署目录
├── onnxruntime.dll            # 共享原生库
├── ocr/                       # OCR 模型
│   ├── PP-OCRv6_det_small.onnx
│   ├── PP-OCRv6_rec_small.onnx
│   └── ch_ppocr_mobile_v2.0_cls_infer.onnx
└── bge-m3/                    # Embedding 模型
    ├── model.onnx
    └── tokenizer.json
```

## 运行要求

- **JVM 参数**：`--enable-native-access=ALL-UNNAMED`（summer-boot 启动脚本已内置）
- **JDK**：25（FFM `java.lang.foreign` 为正式 API）
- **onnxruntime**：1.16~1.20（推荐 1.20.1）

### 为何需要 `--enable-native-access=ALL-UNNAMED`

FFM（`java.lang.foreign`）通过 `SymbolLookup` 加载原生库、`Linker.downcallHandle` 调用 C 函数、`MemorySegment` 直接读写原生内存，这些操作能绕过 JVM 的内存安全与访问控制，有真实风险（野指针、任意代码执行）。因此 JDK 22 起 FFM 转正后，native access 仍是**受限操作**，默认禁止，JVM 要求显式声明授权哪个模块执行 native 调用，未授权调用会抛 `IllegalCallerException`。

`--enable-native-access=<模块>` 接收的是**模块名**，授权粒度到 JPMS 模块层：

- 代码用 `module-info.java` 声明命名模块时，可精确授权：`--enable-native-access=cn.jiebaba.summer.core`
- summer 为传统 classpath 部署（无 `module-info.java`），`OnnxEngine` 所在的 classpath 代码属于**未命名模块**（unnamed module），没有名字，只能用保留标识 `ALL-UNNAMED` 指代 classpath 上所有未模块化代码

`ALL-UNNAMED` 含义是「授权 classpath 上的未命名模块执行 native 访问」，而非「授权所有模块」。若改用 JPMS 命名模块可精确到具体模块名，但会破坏 summer 现有 classpath 打包流程（loader/插件），故沿用 `ALL-UNNAMED`——对本框架自用场景，classpath 代码受控，风险可接受。
