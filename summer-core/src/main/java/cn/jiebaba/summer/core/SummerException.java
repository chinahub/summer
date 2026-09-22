package cn.jiebaba.summer.core;

/** summer 框架统一基础运行期异常，各模块异常类型均以其为根。 */
public class SummerException extends RuntimeException {

    public SummerException(String message) {
        super(message);
    }

    public SummerException(String message, Throwable cause) {
        super(message, cause);
    }
}
