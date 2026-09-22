package cn.jiebaba.summer.support.ocr;

import cn.jiebaba.summer.core.SummerException;

/** OCR 统一运行期异常，封装模型加载、推理与图像处理过程中的错误。 */
public class OcrException extends SummerException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
