package cn.jiebaba.summer.ai.retry;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * 重试策略：最大尝试次数、退避起始/倍率/上限与可重试异常判定。
 * 默认对所有非 {@link CircuitBreakerOpenException} 异常重试。
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double multiplier;
    private final Duration maxBackoff;
    private final Predicate<Throwable> retryOn;

    public RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier,
                       Duration maxBackoff, Predicate<Throwable> retryOn) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.multiplier = multiplier;
        this.maxBackoff = maxBackoff;
        this.retryOn = retryOn == null
                ? t -> !(t instanceof CircuitBreakerOpenException)
                : retryOn;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 当前是否应重试：尝试次数未达上限且异常可重试。 */
    public boolean shouldRetry(Throwable t, int attempt) {
        return attempt < maxAttempts && retryOn.test(t);
    }

    /** 计算第 attempt 次失败后的退避时长（指数退避，封顶 maxBackoff）。 */
    public Duration backoff(int attempt) {
        double factor = Math.pow(multiplier, attempt - 1);
        long millis = (long) (initialBackoff.toMillis() * factor);
        long capped = Math.min(millis, maxBackoff.toMillis());
        return Duration.ofMillis(Math.max(0, capped));
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** 链式构造器，提供常用默认值。 */
    public static class Builder {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private double multiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(20);
        private Predicate<Throwable> retryOn;

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder retryOn(Predicate<Throwable> retryOn) {
            this.retryOn = retryOn;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(maxAttempts, initialBackoff, multiplier, maxBackoff, retryOn);
        }
    }
}
