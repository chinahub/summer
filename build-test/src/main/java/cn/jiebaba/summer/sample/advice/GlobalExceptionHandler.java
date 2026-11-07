package cn.jiebaba.summer.sample.advice;

import cn.jiebaba.summer.web.annotation.ExceptionHandler;
import cn.jiebaba.summer.web.annotation.ResponseStatus;
import cn.jiebaba.summer.web.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    @ResponseStatus(400)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(500)
    public Map<String, Object> handleAny(Exception ex) {
        return Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
        );
    }
}
