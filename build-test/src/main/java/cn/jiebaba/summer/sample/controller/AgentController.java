package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.ai.agent.AgentOrchestrator;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.ai.agent.AgentTask;
import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 进程内多 agent 协作最小示例：writer 产出初稿 → reviewer 审查 → writer 按反馈修订。
 * 桩执行器纯字符串处理，离线可跑，不依赖真实 LLM。
 */
@RestController
public class AgentController {

    @Autowired
    private AgentOrchestrator orchestrator;

    /** 一次完整协作：产出 → 审查 → 修订，返回各阶段结果 JSON。 */
    @GetMapping("/agent/demo")
    public Map<String, Object> demo(@RequestParam(value = "topic", defaultValue = "summer 框架介绍") String topic) {
        Map<String, Object> body = new LinkedHashMap<>();
        String conversationId = "conv-" + System.currentTimeMillis();

        AgentResult draft = orchestrator.dispatch(AgentTask.begin("writer", topic).inConversation(conversationId));
        body.put("draft", draft.success() ? draft.output() : draft.error());

        AgentResult review = orchestrator.dispatch(AgentTask.begin("reviewer", draft.output())
                .inConversation(conversationId));
        body.put("review", review.success() ? review.output() : review.error());

        AgentResult revised = review.success()
                ? orchestrator.dispatch(AgentTask.begin("writer", topic)
                        .inConversation(conversationId)
                        .withFeedback(review.output()))
                : AgentResult.fail(review.error());
        body.put("revised", revised.success() ? revised.output() : revised.error());
        body.put("success", draft.success() && review.success() && revised.success());
        return body;
    }
}
