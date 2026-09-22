package cn.jiebaba.summer.core.onnx;

/** ONNX Runtime 推理统一运行期异常，封装引擎初始化、模型加载与推理过程中的错误。 */
public class OnnxException extends RuntimeException {

    public OnnxException(String message) {
        super(message);
    }

    public OnnxException(String message, Throwable cause) {
        super(message, cause);
    }
}
