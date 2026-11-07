package cn.jiebaba.summer.sample.agent;

import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.ai.agent.AgentTask;
import cn.jiebaba.summer.core.annotation.Component;

/** 审查桩执行器：纯字符串规则审查，不调外部 API（离线可跑）。 */
@Component
public class ReviewerAgentExecutor implements AgentExecutor {

    @Override
    public String name() {
        return "reviewer";
    }

    @Override
    public AgentResult run(AgentTask task) {
        String draft = task.input() == null ? "" : task.input();
        if (draft.length() < 5) {
            return AgentResult.fail("reviewer: 内容过短，审查不通过");
        }
        return AgentResult.ok("结构完整、表述清晰，审查通过", "reviewer: exit=0");
    }
}
