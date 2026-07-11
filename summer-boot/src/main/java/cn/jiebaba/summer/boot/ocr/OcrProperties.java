package cn.jiebaba.summer.boot.ocr;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.office.ocr.OcrConfig;

/**
 * summer.ocr.* 配置项绑定：onnxruntime 原生库路径、PP-OCR 模型与字典路径及可调参数。
 * <p>未配置 lib-path 等必要项时视为未启用，不触发 OCR 引擎初始化。
 */
public class OcrProperties {

    private final String libPath;
    private final String detModelPath;
    private final String clsModelPath;
    private final String recModelPath;
    private final String dictPath;
    private final boolean useCls;
    private final float textScore;
    private final float detUnclipRatio;
    private final float detBoxThresh;
    private final float clsThresh;

    private OcrProperties(String libPath, String detModelPath, String clsModelPath, String recModelPath,
                          String dictPath, boolean useCls, float textScore, float detUnclipRatio,
                          float detBoxThresh, float clsThresh) {
        this.libPath = libPath;
        this.detModelPath = detModelPath;
        this.clsModelPath = clsModelPath;
        this.recModelPath = recModelPath;
        this.dictPath = dictPath;
        this.useCls = useCls;
        this.textScore = textScore;
        this.detUnclipRatio = detUnclipRatio;
        this.detBoxThresh = detBoxThresh;
        this.clsThresh = clsThresh;
    }

    /** 从环境配置解析 OcrProperties。 */
    public static OcrProperties from(Environment env) {
        return new OcrProperties(
                env.getProperty("summer.ocr.lib-path"),
                env.getProperty("summer.ocr.det-model-path"),
                env.getProperty("summer.ocr.cls-model-path"),
                env.getProperty("summer.ocr.rec-model-path"),
                env.getProperty("summer.ocr.dict-path"),
                env.getProperty("summer.ocr.use-cls", Boolean.class, true),
                env.getProperty("summer.ocr.text-score", Float.class, 0.5f),
                env.getProperty("summer.ocr.det-unclip-ratio", Float.class, 1.6f),
                env.getProperty("summer.ocr.det-box-thresh", Float.class, 0.5f),
                env.getProperty("summer.ocr.cls-thresh", Float.class, 0.9f));
    }

    /** 是否已配置必要项（原生库、检测/识别模型、字典齐全）。 */
    public boolean isConfigured() {
        // dictPath 可选：PP-OCRv4/v6 识别模型内嵌 character 字典时无需配置
        return notBlank(libPath) && notBlank(detModelPath) && notBlank(recModelPath);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 转为 {@link OcrConfig}。仅在 {@link #isConfigured()} 为真时调用。 */
    public OcrConfig toConfig() {
        OcrConfig.Builder b = OcrConfig.builder()
                .libPath(libPath)
                .detModelPath(detModelPath)
                .recModelPath(recModelPath)
                .dictPath(dictPath)
                .useCls(useCls)
                .textScore(textScore)
                .detUnclipRatio(detUnclipRatio)
                .detBoxThresh(detBoxThresh)
                .clsThresh(clsThresh);
        if (notBlank(clsModelPath)) {
            b.clsModelPath(clsModelPath);
        }
        return b.build();
    }

    public String getLibPath() { return libPath; }
    public String getDetModelPath() { return detModelPath; }
    public String getClsModelPath() { return clsModelPath; }
    public String getRecModelPath() { return recModelPath; }
    public String getDictPath() { return dictPath; }
    public boolean isUseCls() { return useCls; }
    public float getTextScore() { return textScore; }
}
