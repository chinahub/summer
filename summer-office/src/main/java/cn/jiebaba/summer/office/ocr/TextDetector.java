package cn.jiebaba.summer.office.ocr;

import java.util.List;

/**
 * 文本检测：对图像做 DB 检测预处理（缩放至 32 倍数、归一化）并运行检测模型，
 * 再经 {@link DbPostProcess} 得到文本框。移植自 RapidAI/rapidocr 的 TextDetector + DetPreProcess。
 */
final class TextDetector {

    private final OnnxEngine.Model model;
    private final int limitSideLen;
    private final String limitType;
    private final float[] mean;
    private final float[] std;
    private final DbPostProcess post;

    TextDetector(OnnxEngine.Model model, OcrConfig cfg) {
        this.model = model;
        this.limitSideLen = cfg.detLimitSideLen();
        this.limitType = cfg.detLimitType();
        this.mean = new float[]{0.5f, 0.5f, 0.5f};
        this.std = new float[]{0.5f, 0.5f, 0.5f};
        this.post = new DbPostProcess(cfg.detThresh(), cfg.detBoxThresh(),
                cfg.detMaxCandidates(), cfg.detUnclipRatio(), cfg.detUseDilation());
    }

    /**
     * 检测文本框。框坐标基于输入图像 img 的像素坐标系。
     *
     * @param img 已预处理（缩放至边界、补边）的图像
     * @return 按阅读顺序排序的文本框列表
     */
    List<DbPostProcess.DetBox> detect(ImageUtil.Img img) {
        ImageUtil.Img resized = detResize(img);
        float[] input = ImageUtil.toChwNormalized(resized, mean, std);
        OnnxEngine.Output out = model.run(input, new long[]{1, 3, resized.height, resized.width});
        List<DbPostProcess.DetBox> boxes = post.process(out.data(), resized.width, resized.height,
                img.width, img.height);
        return DbPostProcess.sortBoxes(boxes);
    }

    /** 检测预处理缩放：按 limit 规则缩放并取 32 的整数倍。 */
    private ImageUtil.Img detResize(ImageUtil.Img img) {
        int h = img.height;
        int w = img.width;
        float ratio;
        if ("min".equals(limitType)) {
            ratio = Math.min(h, w) < limitSideLen ? (float) limitSideLen / Math.min(h, w) : 1.0f;
        } else {
            ratio = Math.max(h, w) > limitSideLen ? (float) limitSideLen / Math.max(h, w) : 1.0f;
        }
        int newH = round32(h * ratio);
        int newW = round32(w * ratio);
        if (newH < 32) {
            newH = 32;
        }
        if (newW < 32) {
            newW = 32;
        }
        return ImageUtil.resize(img, newW, newH);
    }

    private static int round32(float v) {
        return Math.round(v / 32f) * 32;
    }
}
