package cn.jiebaba.summer.ai.retry;

import java.time.Duration;

/**
 * 熔断器：CLOSED -> 失败达阈值 -> OPEN（拒绝调用）-> 等待冷却 -> HALF_OPEN（放行试探）-> 成功则 CLOSED/失败则 OPEN。
 * 线程安全，保护下游模型免受持续失败冲击。
 */
public final class CircuitBreaker {

    /** 熔断器状态。 */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration waitDurationInOpenState;
    private State state = State.CLOSED;
    private int failureCount = 0;
    private long openedAtNanos = 0L;

    public CircuitBreaker(int failureThreshold, Duration waitDurationInOpenState) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold 必须为正");
        }
        this.failureThreshold = failureThreshold;
        this.waitDurationInOpenState = waitDurationInOpenState;
    }

    /** 申请调用许可：OPEN 且未冷却时抛 CircuitBreakerOpenException；冷却到期转 HALF_OPEN 放行。 */
    public synchronized void acquirePermission() {
        if (state == State.OPEN) {
            if (System.nanoTime() - openedAtNanos >= waitDurationInOpenState.toNanos()) {
                state = State.HALF_OPEN;
            } else {
                throw new CircuitBreakerOpenException("熔断器开启，拒绝调用");
            }
        }
    }

    /** 记录成功：重置失败计数并闭合熔断器。 */
    public synchronized void recordSuccess() {
        failureCount = 0;
        state = State.CLOSED;
    }

    /** 记录失败：累计计数，HALF_OPEN 或达阈值则开启熔断器。 */
    public synchronized void recordFailure(Throwable t) {
        failureCount++;
        if (state == State.HALF_OPEN || failureCount >= failureThreshold) {
            state = State.OPEN;
            openedAtNanos = System.nanoTime();
        }
    }

    public synchronized State getState() {
        return state;
    }
}
