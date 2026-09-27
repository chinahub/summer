package cn.jiebaba.summer.ai.agent;

/**
 * 进程内 agent 协作策略。
 *
 * @param timeoutSeconds 单次任务同步等待执行结果的上限（秒）；超时判失败并取消执行
 * @param maxDepth       协作链最大深度（嵌套 dispatch 层数上限），超过判失败
 */
public record AgentPolicy(int timeoutSeconds, int maxDepth) {

    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public static final int DEFAULT_MAX_DEPTH = 8;

    public static AgentPolicy ofDefaults() {
        return new AgentPolicy(DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_DEPTH);
    }

    public AgentPolicy {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
        if (maxDepth <= 0) {
            maxDepth = DEFAULT_MAX_DEPTH;
        }
    }
}
