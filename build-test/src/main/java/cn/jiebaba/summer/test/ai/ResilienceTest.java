package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.retry.CircuitBreaker;
import cn.jiebaba.summer.ai.retry.CircuitBreakerOpenException;
import cn.jiebaba.summer.ai.retry.RateLimiter;
import cn.jiebaba.summer.ai.retry.ResilientChatModel;
import cn.jiebaba.summer.ai.retry.RetryPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

/** 重试、限流与熔断策略的单元测试。 */
public class ResilienceTest {

    private Prompt prompt() {
        return new Prompt(List.of(cn.jiebaba.summer.ai.chat.Message.user("hi")));
    }

    @Test
    public void retrySucceedsAfterFailures() {
        StubChatModel stub = new StubChatModel(new ChatResponse("ok", null, "stop", null)).failFirstN(2);
        RetryPolicy retry = RetryPolicy.builder()
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(1))
                .multiplier(1.0)
                .maxBackoff(Duration.ofMillis(5))
                .build();
        ResilientChatModel model = new ResilientChatModel(stub, null, retry, null);
        ChatResponse resp = model.call(prompt());
        Assertions.assertEquals("ok", resp.content());
        Assertions.assertEquals(3, stub.callCount());
    }

    @Test
    public void retryExhaustsAndRethrows() {
        StubChatModel stub = new StubChatModel().failFirstN(99);
        RetryPolicy retry = RetryPolicy.builder()
                .maxAttempts(2)
                .initialBackoff(Duration.ofMillis(1))
                .multiplier(1.0)
                .maxBackoff(Duration.ofMillis(2))
                .build();
        ResilientChatModel model = new ResilientChatModel(stub, null, retry, null);
        Assertions.assertThrows(AiException.class, () -> model.call(prompt()));
        Assertions.assertEquals(2, stub.callCount());
    }

    @Test
    public void circuitBreakerOpensAfterThreshold() {
        StubChatModel stub = new StubChatModel().failFirstN(99);
        CircuitBreaker cb = new CircuitBreaker(2, Duration.ofSeconds(60));
        ResilientChatModel model = new ResilientChatModel(stub, null, null, cb);
        Assertions.assertThrows(AiException.class, () -> model.call(prompt()));
        Assertions.assertThrows(AiException.class, () -> model.call(prompt()));
        Assertions.assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        Assertions.assertThrows(CircuitBreakerOpenException.class, () -> model.call(prompt()));
    }

    @Test
    public void circuitBreakerRecoversAfterCooldown() throws Exception {
        StubChatModel stub = new StubChatModel(new ChatResponse("ok", null, "stop", null)).failFirstN(2);
        CircuitBreaker cb = new CircuitBreaker(2, Duration.ofMillis(50));
        ResilientChatModel model = new ResilientChatModel(stub, null, null, cb);
        Assertions.assertThrows(AiException.class, () -> model.call(prompt()));
        Assertions.assertThrows(AiException.class, () -> model.call(prompt()));
        Assertions.assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        Thread.sleep(80);
        ChatResponse resp = model.call(prompt());
        Assertions.assertEquals("ok", resp.content());
        Assertions.assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    public void rateLimiterAllowsBurstWithoutBlocking() {
        RateLimiter limiter = new RateLimiter(1000);
        long start = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        Assertions.assertTrue(elapsedMs < 100, "高速率限流不应长时间阻塞，实际: " + elapsedMs + "ms");
    }

    @Test
    public void retryPolicyBackoffCapped() {
        RetryPolicy p = RetryPolicy.builder()
                .maxAttempts(5)
                .initialBackoff(Duration.ofMillis(100))
                .multiplier(2.0)
                .maxBackoff(Duration.ofMillis(500))
                .build();
        Assertions.assertTrue(p.backoff(1).toMillis() <= 500);
        Assertions.assertEquals(500, p.backoff(10).toMillis());
        Assertions.assertFalse(p.shouldRetry(new AiException("x"), 5));
    }
}
