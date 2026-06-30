package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.boot.annotation.SummerBootApplication;

/**
 * Self-contained security test application (no database). Launched by
 * {@link SecuritySmokeTest} to exercise JWT login, URL-level and method-level
 * authorization, and {@code @AuthenticationPrincipal} injection end-to-end.
 */
@SummerBootApplication(scanBasePackages = "cn.jiebaba.summer.test.security")
public class SecurityTestApp {
    public static void main(String[] args) {
        // actual startup is driven by SecuritySmokeTest
    }
}
