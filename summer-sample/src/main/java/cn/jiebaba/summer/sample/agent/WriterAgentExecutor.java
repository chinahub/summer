package cn.jiebaba.summer.sample.agent;

import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.ai.agent.AgentTask;
import cn.jiebaba.summer.core.annotation.Component;

/**
 * 写作桩执行器：纯字符串处理，不调外部 API（离线可跑）。
 * 收到上游审查反馈时产出修订稿，演示 {@link AgentTask#feedback()} 重做语义。
 */
@Component
public class WriterAgentExecutor implements AgentExecutor {

    @Override
    public String name() {
        return "writer";
    }

    @Override
    public AgentResult run(AgentTask task) {
        boolean rewrite = task.feedback() != null && !task.feedback().isBlank();
        String draft = rewrite
                ? "《" + task.input() + "》修订稿（已采纳审查意见：" + task.feedback() + "）"
                : "《" + task.input() + "》初稿";
        return AgentResult.ok(draft, "writer: exit=0");
    }
}
