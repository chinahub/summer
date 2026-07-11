package cn.jiebaba.summer.office.ocr;

import java.util.ArrayList;
import java.util.List;

/**
 * 方向分类：对每个文本裁剪图判断 0/180 度，超过阈值则旋转 180 度。移植自 rapidocr TextClassifier。
 * <p>预处理：按宽高比缩放到固定高 imgH，宽不超过 imgW，归一化后右侧零填充。
 */
final class TextClassifier {

    private final OnnxEngine.Model model;
    private final int imgC;
    private final int imgH;
    private final int imgW;
    private final int batchNum;
    private final float thresh;

    TextClassifier(OnnxEngine.Model model, OcrConfig cfg) {
        this.model = model;
        int[] shape = cfg.clsImageShape();
        this.imgC = shape[0];
        this.imgH = shape[1];
        this.imgW = shape[2];
        this.batchNum = cfg.clsBatchNum();
        this.thresh = cfg.clsThresh();
    }

    /**
     * 对裁剪图列表逐批分类，返回校正方向后的图像列表（顺序与输入一致）。
     *
     * @param crops 文本裁剪图
     * @return 方向校正后的图像列表
     */
    List<ImageUtil.Img> classify(List<ImageUtil.Img> crops) {
        List<ImageUtil.Img> out = new ArrayList<>(crops);
        int n = crops.size();
        for (int beg = 0; beg < n; beg += batchNum) {
            int end = Math.min(n, beg + batchNum);
            int batch = end - beg;
            float[] input = new float[batch * imgC * imgH * imgW];
            for (int i = 0; i < batch; i++) {
                resizeNormInto(crops.get(beg + i), input, i * imgC * imgH * imgW);
            }
            OnnxEngine.Output o = model.run(input, new long[]{batch, imgC, imgH, imgW});
            float[] data = o.data();
            for (int i = 0; i < batch; i++) {
                float p0 = data[i * 2];
                float p1 = data[i * 2 + 1];
                int label = p1 > p0 ? 1 : 0;
                if (label == 1 && p1 > thresh) {
                    out.set(beg + i, ImageUtil.rotate180(crops.get(beg + i)));
                }
            }
        }
        return out;
    }

    /** 缩放、归一化并右侧零填充到 [imgC,imgH,imgW]，写入 target 起 offset。 */
    private void resizeNormInto(ImageUtil.Img crop, float[] target, int offset) {
        float ratio = crop.width / (float) crop.height;
        int resizedW = (int) Math.ceil(imgH * ratio);
        if (resizedW > imgW) {
            resizedW = imgW;
        }
        ImageUtil.Img resized = ImageUtil.resize(crop, resizedW, imgH);
        int plane = imgH * imgW;
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < resizedW; x++) {
                int c = resized.rgb[y * resizedW + x];
                target[offset + y * imgW + x] = ((c >> 16 & 0xFF) / 255f - 0.5f) / 0.5f;
                target[offset + plane + y * imgW + x] = ((c >> 8 & 0xFF) / 255f - 0.5f) / 0.5f;
                target[offset + plane * 2 + y * imgW + x] = ((c & 0xFF) / 255f - 0.5f) / 0.5f;
            }
        }
    }
}
