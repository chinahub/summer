package cn.jiebaba.summer.ai.retry;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Prompt;

import java.util.stream.Stream;

/**
 * 弹性 ChatModel：在底层模型之上叠加限流、重试与熔断策略。
 * 三者均可选（null 表示不启用）；同步调用按「限流 -> 熔断许可 -> 调用 -> 成功/失败记录 -> 失败则退避重试」执行。
 * 流式调用仅做限流与熔断许可，不重试（流一旦开始不再回退）。
 */
public class ResilientChatModel implements ChatModel {

    private final ChatModel delegate;
    private final RateLimiter rateLimiter;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;

    public ResilientChatModel(ChatModel delegate, RateLimiter rateLimiter,
                              RetryPolicy retryPolicy, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.retryPolicy = retryPolicy;
        this.circuitBreaker = circuitBreaker;
    }

    /** 同步调用：限流后按熔断+重试策略执行。 */
    @Override
    public ChatResponse call(Prompt prompt) {
        int attempt = 0;
        while (true) {
            attempt++;
            acquireRate();
            if (circuitBreaker != null) {
                circuitBreaker.acquirePermission();
            }
            try {
                ChatResponse resp = delegate.call(prompt);
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
                return resp;
            } catch (RuntimeException e) {
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure(e);
                }
                if (retryPolicy == null || !retryPolicy.shouldRetry(e, attempt)) {
                    throw e;
                }
                sleepBackoff(retryPolicy.backoff(attempt));
            }
            // 重试：回到循环顶部再次尝试
        }
    }

    /** 流式调用：限流与熔断许可后透传底层流，不重试。 */
    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        acquireRate();
        if (circuitBreaker != null) {
            circuitBreaker.acquirePermission();
        }
        return delegate.stream(prompt);
    }

    /** 获取限流令牌，未启用限流则直接返回。 */
    private void acquireRate() {
        if (rateLimiter != null) {
            rateLimiter.acquire();
        }
    }

    /** 按退避时长休眠，中断则抛出异常。 */
    private void sleepBackoff(java.time.Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("重试退避等待被中断", e);
        }
    }
}
