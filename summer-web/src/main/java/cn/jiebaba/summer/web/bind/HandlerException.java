package cn.jiebaba.summer.web.bind;

public class HandlerException extends RuntimeException {
    public HandlerException(String message) { super(message); }
    public HandlerException(String message, Throwable cause) { super(message, cause); }
}
