package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.admin.auth.captcha.CaptchaResult;
import cn.jiebaba.summer.admin.auth.captcha.CaptchaService;
import cn.jiebaba.summer.admin.auth.captcha.InMemoryCaptchaStore;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一次性图形验证码单元测试：错误码失败、成功即消费（重放失败）、失败尝试按设计消费、
 * 两分钟过期、不同 UUID 相互隔离。测试注入确定性验证码生成器，不依赖随机数。
 */
class CaptchaServiceTest {

    /** 测试验证码服务：固定 TTL 120 秒、固定码序列。 */
    private static CaptchaService service(Queue<String> codes) {
        return new CaptchaService(new InMemoryCaptchaStore(), true, 120, codes::poll);
    }

    @Test
    void issueReturnsUuidAndBase64Image() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        CaptchaResult result = service.issue();
        assertNotNull(result.getUuid());
        assertFalse(result.getUuid().isBlank());
        assertNotNull(result.getImg());
        assertTrue(result.getImg().startsWith("data:image/png;base64,"), "验证码图片应为 PNG data URL");
        assertTrue(result.isCaptchaEnabled());
    }

    @Test
    void correctCodeVerifiesSuccessfully() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        CaptchaResult result = service.issue();
        assertTrue(service.verify(result.getUuid(), "ABCD"));
    }

    @Test
    void wrongCodeFails() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        CaptchaResult result = service.issue();
        assertFalse(service.verify(result.getUuid(), "WXYZ"));
    }

    @Test
    void successIsOneTimeAndReplayFails() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        CaptchaResult result = service.issue();
        assertTrue(service.verify(result.getUuid(), "ABCD"), "首次校验应成功");
        assertFalse(service.verify(result.getUuid(), "ABCD"), "重放同一 UUID 必须失败（一次性）");
    }

    @Test
    void failedAttemptConsumesCaptcha() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        CaptchaResult result = service.issue();
        assertFalse(service.verify(result.getUuid(), "WXYZ"), "错误码应失败");
        assertFalse(service.verify(result.getUuid(), "ABCD"),
                "失败后原验证码必须失效，不能继续尝试同一 UUID");
    }

    @Test
    void expiredCaptchaFails() {
        // TTL=0：签发即过期，验证必须失败
        CaptchaService service = new CaptchaService(new InMemoryCaptchaStore(), true, 0,
                new ConcurrentLinkedQueue<>(java.util.List.of("ABCD"))::poll);
        CaptchaResult result = service.issue();
        assertFalse(service.verify(result.getUuid(), "ABCD"));
    }

    @Test
    void blankUuidOrCodeFails() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        CaptchaResult result = service.issue();
        assertFalse(service.verify("", "ABCD"));
        assertFalse(service.verify(result.getUuid(), ""));
        assertFalse(service.verify(null, "ABCD"));
    }

    @Test
    void differentUuidsAreIsolated() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("AAAA", "BBBB")));
        CaptchaResult first = service.issue();
        CaptchaResult second = service.issue();
        assertFalse(service.verify(first.getUuid(), "BBBB"), "不能用第二个验证码的码通过第一个 UUID");
        assertTrue(service.verify(second.getUuid(), "BBBB"), "第二个 UUID 校验不应受第一个影响");
    }

    @Test
    void unknownUuidFails() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("ABCD")));
        service.issue();
        assertFalse(service.verify("no-such-uuid", "ABCD"));
    }

    @Test
    void verifyIsCaseInsensitive() {
        CaptchaService service = service(new ConcurrentLinkedQueue<>(java.util.List.of("aB3d")));
        CaptchaResult result = service.issue();
        assertTrue(service.verify(result.getUuid(), "AB3D"));
    }

    @Test
    void disabledCaptchaAlwaysPasses() {
        CaptchaService service = new CaptchaService(new InMemoryCaptchaStore(), false, 120,
                new ConcurrentLinkedQueue<>(java.util.List.of("ABCD"))::poll);
        assertFalse(service.issue().isCaptchaEnabled());
        assertTrue(service.verify(null, null), "验证码关闭时校验必须放行");
        assertEquals(0, service.pendingCount(), "关闭时不应保存任何验证码");
    }
}
