package cn.jiebaba.summer.office;

/** summer-office 统一运行期异常，封装文档解析与生成过程中的错误。 */
public class OfficeException extends RuntimeException {

    public OfficeException(String message) {
        super(message);
    }

    public OfficeException(String message, Throwable cause) {
        super(message, cause);
    }
}
