package cn.jiebaba.summer.ai.agent;

/** A2A 任务执行结果：输出文本（如 markdown 产物）、证据回执与错误信息。 */
public record AgentResult(boolean success, String output, String evidence, String error) {

    public static AgentResult ok(String output, String evidence) {
        return new AgentResult(true, output, evidence, null);
    }

    public static AgentResult fail(String error) {
        return new AgentResult(false, null, null, error);
    }
}
