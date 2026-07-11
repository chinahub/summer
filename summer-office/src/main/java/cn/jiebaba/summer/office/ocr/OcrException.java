package cn.jiebaba.summer.office.ocr;

import cn.jiebaba.summer.office.OfficeException;

/** OCR 统一运行期异常，封装模型加载、推理与图像处理过程中的错误。 */
public class OcrException extends OfficeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
