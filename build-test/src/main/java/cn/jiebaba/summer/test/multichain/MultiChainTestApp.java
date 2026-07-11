package cn.jiebaba.summer.test.multichain;

import cn.jiebaba.summer.boot.annotation.SummerBootApplication;

/**
 * 多 SecurityFilterChain 集成测试应用（无数据库）。由 {@link MultiChainSmokeTest} 启动，
 * 验证两条安全链按请求路径分流：{@code /api/**} 走 JWT 受保护链，其余走放行兜底链。
 */
@SummerBootApplication(scanBasePackages = "cn.jiebaba.summer.test.multichain")
public class MultiChainTestApp {
    public static void main(String[] args) {
        // 实际启动由 MultiChainSmokeTest 驱动
    }
}
