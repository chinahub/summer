# Summer OCR（summer-office OCR）

> summer-office OCR -- 基于 JDK 25 Foreign Function & Memory API（`java.lang.foreign`）直连 ONNX Runtime、纯 Java 移植 RapidAI/rapidocr 的 PP-OCR 流水线，零第三方 Java 依赖。

summer-office 在 `cn.jiebaba.summer.office.ocr` 包下提供 OCR 能力：检测（DB）→ 方向分类 → 识别（CRNN+CTC）全流水线均用纯 JDK（`javax.imageio` + 手写像素/几何运算）实现，仅通过 FFM 调用本地 `onnxruntime` 共享库完成神经网络推理。无需 JNI 胶水代码、无需引入 onnxruntime 的 Java 绑定包，原生库即唯一运行期外部依赖（类比 JDBC 驱动）。

## 设计动机

参考的 [MyMonsterCat/RapidOcr-Java](https://github.com/MyMonsterCat/RapidOcr-Java) 实质是 **JNI 封装**：其 `OcrEngine` 声明 `native` 方法，检测/分类/识别全部在平台相关的 C++ 动态库中完成。这与本框架「核心零第三方依赖、纯 JDK」的理念冲突，且带来平台库打包负担。

本实现选择 **FFM 路线**（`java.lang.foreign`，JDK 22 起稳定、JDK 25 LTS 内置）：

- 直接 `SymbolLookup.libraryLookup` 加载 `onnxruntime.dll`/`libonnxruntime.so`，按 `OrtApi` 结构体字段序号读取函数指针包装为 `MethodHandle`；
- det/cls/rec 的预处理与后处理（缩放、归一化、DB 二值化、连通域、最小外接矩形、透视矫正、CTC 解码）全部纯 Java 移植自 [RapidAI/rapidocr](https://github.com/RapidAI/rapidocr)（Python）；
- 因此 **不引入任何 Maven 依赖**，`summer-office/pom.xml` 无需改动；onnxruntime 原生库与模型文件作为部署期资产按路径加载。

## 快速开始

### 1. 准备运行期资产

OCR 需要三类资产（均为部署期文件，不打包进 JAR）：

| 资产 | 说明 | 获取来源 |
| --- | --- | --- |
| onnxruntime 原生库 | `onnxruntime.dll`（Windows）/ `libonnxruntime.so`（Linux）/ `libonnxruntime.dylib`（macOS） | [onnxruntime releases](https://github.com/microsoft/onnxruntime/releases)（选 CPU 版，如 `onnxruntime-win-x64-1.20.1.zip` 解压取 `lib/` 下 dll） |
| PP-OCR 模型 | 检测 `PP-OCRv6_det_*.onnx`、分类 `ch_ppocr_mobile_v2.0_cls_*.onnx`、识别 `PP-OCRv6_rec_*.onnx` | ModelScope `RapidAI/RapidOCR`（tag `v3.9.1`）下 `onnx/PP-OCRv6/`；v3/v4 旧模型见 [RapidOcr-Java](https://github.com/MyMonsterCat/RapidOcr-Java/tree/main/rapidocr-onnx-models/src/main/resources/models) |
| 字典 | `ppocr_keys_v1.txt`（识别字符表，每行一个字符） | 同上模型目录。**v4/v6 识别模型已内嵌字典，可省略** |

### 2. 直接使用

PP-OCRv6 为默认推荐版本：检测/识别模型支持中、英、日、韩等多语言统一识别（字符表约 18708 个，内嵌于模型元数据），无需单独字典文件；分类复用 v4 的 `ch_ppocr_mobile_v2.0_cls`（v6 无独立 cls 模型）：

```java
import cn.jiebaba.summer.office.ocr.Ocr;
import cn.jiebaba.summer.office.ocr.OcrConfig;
import cn.jiebaba.summer.office.ocr.OcrResult;

OcrConfig config = OcrConfig.builder()
        .libPath("C:/ocr/onnxruntime.dll")                                  // onnxruntime 原生库
        .detModelPath("C:/ocr/PP-OCRv6_det_small.onnx")                     // 或 _tiny / _medium
        .clsModelPath("C:/ocr/ch_ppocr_mobile_v2.0_cls_infer.onnx")         // v6 复用 v4 cls，可空则跳过方向分类
        .recModelPath("C:/ocr/PP-OCRv6_rec_small.onnx")
        // 无需 dictPath：v6 识别模型内嵌 character 字典
        .build();

try (Ocr ocr = Ocr.create(config)) {
    OcrResult result = ocr.recognize(Files.readAllBytes(Path.of("receipt.png")));
    System.out.println(result.text());          // 全文（各文本块以换行拼接）
    for (var item : result.items()) {           // 逐块：文本 + 4 点定位框 + 置信度
        System.out.printf("%.3f  %s%n", item.score(), item.text());
    }
}
```

> 模型下载：ModelScope `RapidAI/RapidOCR`（tag `v3.9.1`）下 `onnx/PP-OCRv6/det/PP-OCRv6_det_small.onnx`、`onnx/PP-OCRv6/rec/PP-OCRv6_rec_small.onnx`。

> 运行需启用原生访问：`java --enable-native-access=ALL-UNNAMED ...`（summer-boot 启动脚本已内置）。

> 回退 PP-OCRv4（旧模型）：将 det/rec 换为 `ch_PP-OCRv4_det_infer.onnx` / `ch_PP-OCRv4_rec_infer.onnx` 即可，v4 识别模型同样内嵌字典；预处理参数与 v6 一致，无需调整。

### 3. 在 summer-boot 中自动装配

`summer-boot` 以 `optional` 引入 `summer-office`；当 `summer.ocr.lib-path` 等配置齐全时自动装配 `Ocr` Bean（`@Lazy`，注入时才初始化引擎，未用不影响启动）：

```yaml
summer:
  ocr:
    lib-path: C:/ocr/onnxruntime.dll
    det-model-path: C:/ocr/PP-OCRv6_det_small.onnx              # PP-OCRv6（默认推荐）
    cls-model-path: C:/ocr/ch_ppocr_mobile_v2.0_cls_infer.onnx  # v6 复用 v4 cls
    rec-model-path: C:/ocr/PP-OCRv6_rec_small.onnx
    # dict-path 无需配置：v6 识别模型内嵌 character 字典
    use-cls: true
    text-score: 0.5        # 识别置信度过滤阈值
    det-unclip-ratio: 1.6  # 文本框外扩比例
    cls-thresh: 0.9        # 方向分类翻转阈值

# 回退 PP-OCRv4（旧模型）：预处理参数一致，仅替换模型文件
#  det-model-path: C:/ocr/ch_PP-OCRv4_det_infer.onnx
#  rec-model-path: C:/ocr/ch_PP-OCRv4_rec_infer.onnx
#  dict-path: C:/ocr/ppocr_keys_v1.txt          # v4 也可省略（模型内嵌字典）
```

```java
@Controller
public class OcrController {
    private final Ocr ocr;
    public OcrController(Ocr ocr) { this.ocr = ocr; }

    @Post("/ocr")
    public OcrResult recognize(@RequestParam("file") MultipartFile file) throws IOException {
        return ocr.recognize(file.getBytes());
    }
}
```

## API

- `Ocr.create(OcrConfig)` -- 加载原生库与模型，返回线程安全的 `Ocr`（`AutoCloseable`，关闭释放会话）。
- `Ocr.recognize(byte[] imageBytes)` -- 返回 `OcrResult`。
- `OcrResult` -- `text()` 拼接全文；`items()` 各文本块。
- `OcrItem` -- `text()` 文本、`box()` 4 角点（左上→右上→右下→左下，原图像素坐标）、`score()` 置信度。

## 更换 PaddleOCR 模型（v3 / v4 / v5 / v6）

RapidOcr-Java 自带 **PP-OCRv3 与 v4** 模型；PaddleOCR 后续发布了 **v5（2025）** 与 **v6**，RapidAI/rapidocr 已支持到 v6。本实现的预处理/后处理按 rapidocr 算法实现，与具体模型版本解耦，**切换模型只需更换文件并按需调整尺寸**：

| 模块 | v3 / v4 | v5 | v6 | 切换要点 |
| --- | --- | --- | --- | --- |
| 检测 det | DB，mean/std=0.5，unclip=1.6 | 同 | 同 | **完全兼容**，直接替换 `det-model-path` |
| 分类 cls | `[3,48,192]`（`ch_ppocr_mobile_v2.0_cls`） | `[3,80,160]`（`PP-LCNet_x0_25`） | **无独立 cls，复用 v4** `[3,48,192]` | v5 需换模型并 `clsImageShape(new int[]{3,80,160})`；v6 直接用 v4 cls |
| 识别 rec | imgH=48，内嵌字典（≈6623 字符，输出 6625 类） | 内嵌字典 | 内嵌字典（≈18708 字符，输出 18710 类，多语言） | 替换 `rec-model-path`；**字典已内嵌于 ONNX 元数据，无需 `dict-path`** |

要点：

- **检测模型跨版本通用**：PP-OCR 各版 det 均为 DB 结构，输入归一化 `(pixel/255-0.5)/0.5`、输出概率图、DB 后处理（二值化→连通域→最小外接矩形→unclip）完全一致，可直接替换。
- **识别字典内嵌优先**：PP-OCRv4/v6 识别模型将字符表内嵌于 ONNX 元数据（`character` 键，换行分隔）。`Ocr` 优先读取内嵌字典，仅在模型未内嵌时回退到 `dict-path` 外部文件；输出类别数 = 字典字符数 + blank + space（v6 为 18708+2=18710），按模型实际输出维度解码，无需手动对齐。
- **cls 尺寸按模型调整**：v3/v4 与 v6（复用 v4 cls）为 `[3,48,192]`，v5 为 `[3,80,160]`，须在配置中调整 `clsImageShape`。v6 无独立 cls 模型，直接使用 v4 的 `ch_ppocr_mobile_v2.0_cls`。
- 模型来源：v5/v6 的 ONNX 可从 [RapidAI/rapidocr](https://github.com/RapidAI/rapidocr) releases 或 ModelScope `RapidAI/RapidOCR` 下载；也可用 `paddle2onnx` 从 [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) 推理模型自行转换。

## 配置参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `libPath` | -- | onnxruntime 原生库路径（必填） |
| `detModelPath` | -- | 检测模型路径（必填） |
| `clsModelPath` | -- | 分类模型路径（可空，跳过方向分类） |
| `recModelPath` | -- | 识别模型路径（必填） |
| `dictPath` | -- | 识别字典路径（**可选**：v4/v6 模型内嵌字典时省略，未内嵌时必填） |
| `useDet` / `useCls` / `useRec` | true | 各阶段开关 |
| `textScore` | 0.5 | 识别置信度过滤阈值 |
| `detUnclipRatio` | 1.6 | 文本框外扩比例 |
| `detBoxThresh` | 0.5 | 文本框得分阈值 |
| `clsThresh` | 0.9 | 方向分类翻转阈值 |
| `clsImageShape` | [3,48,192] | 分类输入尺寸（v5 改 [3,80,160]） |
| `recImageShape` | [3,48,320] | 识别输入尺寸 |
| `minSideLen` / `maxSideLen` | 30 / 2000 | 预处理图像边界 |

## 实现说明

- `OnnxEngine` -- FFM 绑定 onnxruntime C API（`OrtGetApiBase`→`GetApi`→`OrtApi` 函数指针表），字段序号对应 `ORT_API_VERSION=20`（1.16~1.20 兼容）；`Model.getCustomMetadata(key)` 经 `SessionGetModelMetadata`/`ModelMetadataLookupCustomMetadataMap` 读取 ONNX 自定义元数据，用于获取 PP-OCRv4/v6 识别模型内嵌的 `character` 字典。
- `ImageUtil` -- 纯 JDK 图像解码（`ImageIO`）、双线性缩放、透视矫正（单应矩阵 + 反向采样）、旋转、归一化。
- `Geometry` -- 凸包（单调链）、最小外接矩形（旋转卡壳）、多边形外扩（miter 偏移近似 Clipper unclip）。
- `DbPostProcess` -- DB 后处理：阈值化、膨胀、8 连通域、最小外接矩形、框内概率均值评分、外扩、过滤排序。
- `TextDetector` / `TextClassifier` / `TextRecognizer` -- 三阶段预处理与推理编排；识别采用 CTC 解码（去重复、去 blank）。
- `Ocr` -- 门面，串联 预处理→补边→检测→透视裁剪→分类→识别→置信度过滤→坐标回映。
