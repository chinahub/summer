package cn.jiebaba.summer.office.ocr;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR 配置：onnxruntime 原生库路径、PP-OCR 模型与字典路径，以及检测/分类/识别参数。
 * <p>参数默认值对齐 RapidAI/rapidocr（默认 PP-OCRv6，预处理与 v4 一致）：det(limit=min/736, thresh=0.3, boxThresh=0.5,
 * unclip=1.6, dilation)、cls(shape=[3,48,192], thresh=0.9)、rec(shape=[3,48,320])。
 * 切换模型版本时仅需替换模型文件；PP-OCRv4/v6 识别模型内嵌 character 字典，dictPath 可省略，
 * 按需调整 cls/rec 图像尺寸（如 PP-OCRv5 cls 为 [3,80,160]）。
 */
public final class OcrConfig {

    private String libPath;
    private String detModelPath;
    private String clsModelPath;
    private String recModelPath;
    private String dictPath;

    private boolean useDet = true;
    private boolean useCls = true;
    private boolean useRec = true;

    private float textScore = 0.5f;
    private int minSideLen = 30;
    private int maxSideLen = 2000;
    private int minHeight = 30;
    private float widthHeightRatio = 8f;

    private int detLimitSideLen = 736;
    private String detLimitType = "min";
    private float detThresh = 0.3f;
    private float detBoxThresh = 0.5f;
    private int detMaxCandidates = 1000;
    private float detUnclipRatio = 1.6f;
    private boolean detUseDilation = true;

    private int[] clsImageShape = {3, 48, 192};
    private int clsBatchNum = 6;
    private float clsThresh = 0.9f;

    private int[] recImageShape = {3, 48, 320};
    private int recBatchNum = 6;

    private OcrConfig() {
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    String libPath() { return libPath; }
    String detModelPath() { return detModelPath; }
    String clsModelPath() { return clsModelPath; }
    String recModelPath() { return recModelPath; }
    String dictPath() { return dictPath; }
    boolean useDet() { return useDet; }
    boolean useCls() { return useCls; }
    boolean useRec() { return useRec; }
    float textScore() { return textScore; }
    int minSideLen() { return minSideLen; }
    int maxSideLen() { return maxSideLen; }
    int minHeight() { return minHeight; }
    float widthHeightRatio() { return widthHeightRatio; }
    int detLimitSideLen() { return detLimitSideLen; }
    String detLimitType() { return detLimitType; }
    float detThresh() { return detThresh; }
    float detBoxThresh() { return detBoxThresh; }
    int detMaxCandidates() { return detMaxCandidates; }
    float detUnclipRatio() { return detUnclipRatio; }
    boolean detUseDilation() { return detUseDilation; }
    int[] clsImageShape() { return clsImageShape; }
    int clsBatchNum() { return clsBatchNum; }
    float clsThresh() { return clsThresh; }
    int[] recImageShape() { return recImageShape; }
    int recBatchNum() { return recBatchNum; }

    /** 校验必要路径存在。 */
    void validate() {
        require(libPath, "libPath");
        require(detModelPath, "detModelPath");
        require(recModelPath, "recModelPath");
        // dictPath 可选：PP-OCRv4/v6 识别模型内嵌 character 字典时无需提供
        if (!Files.exists(Path.of(libPath))) {
            throw new OcrException("onnxruntime 库不存在：" + libPath);
        }
    }

    private static void require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new OcrException("OCR 配置缺失：" + name);
        }
    }

    /** 配置构建器。 */
    public static final class Builder {
        private final OcrConfig c = new OcrConfig();

        /** onnxruntime 共享库路径（Windows: onnxruntime.dll，Linux: libonnxruntime.so）。 */
        public Builder libPath(String v) { c.libPath = v; return this; }
        /** 检测模型路径（如 ch_PP-OCRv4_det_infer.onnx）。 */
        public Builder detModelPath(String v) { c.detModelPath = v; return this; }
        /** 方向分类模型路径（如 ch_ppocr_mobile_v2.0_cls_infer.onnx），可空表示跳过分类。 */
        public Builder clsModelPath(String v) { c.clsModelPath = v; return this; }
        /** 识别模型路径（如 ch_PP-OCRv4_rec_infer.onnx）。 */
        public Builder recModelPath(String v) { c.recModelPath = v; return this; }
        /** 字典路径（如 ppocr_keys_v1.txt）。 */
        public Builder dictPath(String v) { c.dictPath = v; return this; }
        public Builder useDet(boolean v) { c.useDet = v; return this; }
        public Builder useCls(boolean v) { c.useCls = v; return this; }
        public Builder useRec(boolean v) { c.useRec = v; return this; }
        public Builder textScore(float v) { c.textScore = v; return this; }
        public Builder detUnclipRatio(float v) { c.detUnclipRatio = v; return this; }
        public Builder detBoxThresh(float v) { c.detBoxThresh = v; return this; }
        public Builder clsThresh(float v) { c.clsThresh = v; return this; }
        public Builder clsImageShape(int[] v) { c.clsImageShape = v; return this; }
        public Builder recImageShape(int[] v) { c.recImageShape = v; return this; }
        public Builder detLimitSideLen(int v) { c.detLimitSideLen = v; return this; }

        public OcrConfig build() {
            c.validate();
            return c;
        }
    }
}
