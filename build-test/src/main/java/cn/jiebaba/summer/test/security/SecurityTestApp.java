package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.boot.annotation.SummerBootApplication;

/**
 * 自包含的安全测试应用（无数据库）。由 {@link SecuritySmokeTest} 启动，
 * 端到端验证 JWT 登录、URL 级与方法级授权以及 {@code @AuthenticationPrincipal} 注入。
 */
@SummerBootApplication(scanBasePackages = "cn.jiebaba.summer.test.security")
public class SecurityTestApp {
    public static void main(String[] args) {
        // 实际启动由 SecuritySmokeTest 驱动
    }
}
