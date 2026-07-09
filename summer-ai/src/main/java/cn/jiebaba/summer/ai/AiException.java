package cn.jiebaba.summer.ai;

/** summer-ai 统一运行期异常，封装大模型调用与解析过程中的错误。 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
