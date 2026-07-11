package cn.jiebaba.summer.office.ocr;

/**
 * OCR 单个文本块：识别文本、定位框（4 角点，左上→右上→右下→左下）与置信度。
 * <p>定位框坐标基于原图像素坐标系。
 */
public record OcrItem(String text, float[][] box, float score) {

    /** 角点 X 坐标数组（左上、右上、右下、左下）。 */
    public float[] xs() {
        return new float[]{box[0][0], box[1][0], box[2][0], box[3][0]};
    }

    /** 角点 Y 坐标数组（左上、右上、右下、左下）。 */
    public float[] ys() {
        return new float[]{box[0][1], box[1][1], box[2][1], box[3][1]};
    }
}
