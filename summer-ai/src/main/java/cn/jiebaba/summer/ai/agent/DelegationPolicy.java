package cn.jiebaba.summer.ai.agent;

/**
 * 委派策略。
 *
 * @param timeoutSeconds 出域委派同步等待对端结果的上限（秒）；超时判失败、契约落 FAILED 可重做
 * @param localExecutor  接域任务的本地执行器名；空 = 取第一个注册的 {@link AgentExecutor}。
 *                       结构性防环：接域执行表固定排除 a2a 网关自身——接域任务不得再委派。
 */
public record DelegationPolicy(int timeoutSeconds, String localExecutor) {

    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public static DelegationPolicy ofDefaults() {
        return new DelegationPolicy(DEFAULT_TIMEOUT_SECONDS, "");
    }

    public DelegationPolicy {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
        localExecutor = localExecutor == null ? "" : localExecutor.trim();
    }
}
