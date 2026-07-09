package cn.jiebaba.summer.ai.retry;

import cn.jiebaba.summer.ai.AiException;

/**
 * 熔断器开启时抛出：调用被快速拒绝，避免持续冲击下游模型。
 */
public class CircuitBreakerOpenException extends AiException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
