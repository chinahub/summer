package cn.jiebaba.summer.office.ocr;

import java.util.List;

/**
 * OCR 识别结果：包含每个文本块（文本、定位框、置信度）与拼接后的全文。
 * <p>全文按从上到下、从左到右的阅读顺序以换行拼接。
 */
public final class OcrResult {

    private final List<OcrItem> items;
    private final String text;

    OcrResult(List<OcrItem> items) {
        this.items = List.copyOf(items);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(items.get(i).text());
        }
        this.text = sb.toString();
    }

    /** 全部文本块。 */
    public List<OcrItem> items() {
        return items;
    }

    /** 拼接全文（各块以换行分隔）。 */
    public String text() {
        return text;
    }

    /** 文本块数量。 */
    public int size() {
        return items.size();
    }

    @Override
    public String toString() {
        return text;
    }
}
