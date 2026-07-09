package cn.jiebaba.summer.ai.retry;

import cn.jiebaba.summer.ai.AiException;

/**
 * 令牌桶限流器：按 permitsPerSecond 速率发放令牌，acquire 阻塞直到获取一个令牌。
 * 适合对大模型调用做并发/频率控制，纯 JDK 实现，线程安全。
 */
public final class RateLimiter {

    private final double permitsPerSecond;
    private double availableTokens;
    private long lastRefillNanos;

    public RateLimiter(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond 必须为正");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.availableTokens = permitsPerSecond;
        this.lastRefillNanos = System.nanoTime();
    }

    /** 阻塞获取一个令牌，令牌不足时按速率等待。 */
    public void acquire() {
        while (true) {
            long sleepNanos;
            synchronized (this) {
                refill();
                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0;
                    return;
                }
                sleepNanos = (long) Math.ceil((1.0 - availableTokens) / permitsPerSecond * 1_000_000_000L);
            }
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AiException("限流等待被中断", e);
                }
            }
        }
    }

    /** 按经过时间补充令牌，封顶为 permitsPerSecond。 */
    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        availableTokens = Math.min(permitsPerSecond, availableTokens + elapsedSeconds * permitsPerSecond);
        lastRefillNanos = now;
    }
}
