package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.AgentOrchestrator;
import cn.jiebaba.summer.ai.agent.AgentPolicy;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.ai.agent.AgentTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AgentOrchestrator 进程内编排测试：按技能路由、未知技能快速失败、超时、异步/并行、
 * ScopedValue 协作链防环与限深、feedback/context 透传、执行器异常兜底。
 */
public class AgentOrchestratorTest {

    private AgentOrchestrator orchestrator(List<AgentExecutor> executors) {
        return new AgentOrchestrator(() -> executors, AgentPolicy.ofDefaults());
    }

    /** 桩执行器：回显技能名与输入。 */
    static class EchoExecutor implements AgentExecutor {
        private final String name;

        EchoExecutor(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public AgentResult run(AgentTask task) {
            return AgentResult.ok(name + ":" + task.input(), "echo: exit=0");
        }
    }

    @Test
    public void dispatchRoutesBySkill() {
        AgentOrchestrator orchestrator = orchestrator(
                List.of(new EchoExecutor("writer"), new EchoExecutor("reviewer")));
        AgentResult result = orchestrator.dispatch(AgentTask.begin("reviewer", "审查一段文字"));
        Assertions.assertTrue(result.success(), result.error());
        Assertions.assertEquals("reviewer:审查一段文字", result.output());
        Assertions.assertTrue(orchestrator.skills().containsAll(List.of("writer", "reviewer")));
    }

    @Test
    public void unknownSkillFailsFast() {
        AgentOrchestrator orchestrator = orchestrator(List.of(new EchoExecutor("writer")));
        AgentResult result = orchestrator.dispatch("translate", "一段文字");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(result.error().contains("未注册的执行器: translate"), result.error());
        Assertions.assertTrue(result.error().contains("writer"), "已注册名录应出现在错误信息中: " + result.error());
    }

    @Test
    public void timeoutFails() {
        AgentExecutor slow = new AgentExecutor() {
            @Override
            public String name() {
                return "slow";
            }

            @Override
            public AgentResult run(AgentTask task) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return AgentResult.ok("done", "slow: exit=0");
            }
        };
        AgentOrchestrator orchestrator =
                new AgentOrchestrator(() -> List.of(slow), new AgentPolicy(1, 8));
        long start = System.currentTimeMillis();
        AgentResult result = orchestrator.dispatch("slow", "等很久");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(result.error().contains("执行超时"), result.error());
        Assertions.assertTrue(System.currentTimeMillis() - start < 8000, "应快速超时返回");
    }

    @Test
    public void submitAsyncReturnsResult() {
        AgentOrchestrator orchestrator = orchestrator(List.of(new EchoExecutor("writer")));
        CompletableFuture<AgentResult> future = orchestrator.submit(AgentTask.begin("writer", "异步草稿"));
        AgentResult result = future.join();
        Assertions.assertTrue(result.success(), result.error());
        Assertions.assertEquals("writer:异步草稿", result.output());
    }

    @Test
    public void dispatchAllParallelOrdered() {
        AgentOrchestrator orchestrator = orchestrator(
                List.of(new EchoExecutor("a"), new EchoExecutor("b")));
        List<AgentResult> results = orchestrator.dispatchAll(List.of(
                AgentTask.begin("a", "一"), AgentTask.begin("b", "二"), AgentTask.begin("a", "三")));
        Assertions.assertEquals(3, results.size());
        Assertions.assertEquals("a:一", results.get(0).output());
        Assertions.assertEquals("b:二", results.get(1).output());
        Assertions.assertEquals("a:三", results.get(2).output());
    }

    @Test
    public void loopGuardOnSelfDispatch() {
        AgentOrchestrator[] ref = new AgentOrchestrator[1];
        AgentExecutor loop = new AgentExecutor() {
            @Override
            public String name() {
                return "loop";
            }

            @Override
            public AgentResult run(AgentTask task) {
                // 嵌套派发同名技能：协作链已含 loop，应被判环
                return ref[0].dispatch(task.skill(), task.input());
            }
        };
        ref[0] = orchestrator(List.of(loop));
        AgentResult result = ref[0].dispatch("loop", "自旋");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(result.error().contains("回环"), result.error());
        Assertions.assertTrue(result.error().contains("loop"), result.error());
    }

    @Test
    public void depthLimitOnChain() {
        AgentOrchestrator[] ref = new AgentOrchestrator[1];
        AgentExecutor a = chainExecutor("a", "b", ref);
        AgentExecutor b = chainExecutor("b", "c", ref);
        AgentExecutor c = chainExecutor("c", "d", ref);
        AgentExecutor d = new EchoExecutor("d");
        ref[0] = new AgentOrchestrator(() -> List.of(a, b, c, d), new AgentPolicy(10, 3));
        // a → b → c → d：链路长度达到 maxDepth=3 后再派发即触发深度上限（技能名互不相同，先触发限深而非判环）
        AgentResult result = ref[0].dispatch("a", "逐级协作");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(result.error().contains("超过最大协作深度"), result.error());
    }

    /** 构造把任务转派给 next 技能的执行器（链式协作样本）。 */
    private static AgentExecutor chainExecutor(String name, String next, AgentOrchestrator[] ref) {
        return new AgentExecutor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AgentResult run(AgentTask task) {
                return ref[0].dispatch(next, task.input());
            }
        };
    }

    @Test
    public void feedbackAndContextVisibleInExecutor() {
        AgentExecutor observer = new AgentExecutor() {
            @Override
            public String name() {
                return "observer";
            }

            @Override
            public AgentResult run(AgentTask task) {
                return AgentResult.ok("feedback=" + task.feedback() + ",lang=" + task.context().get("lang"),
                        "observer: exit=0");
            }
        };
        AgentOrchestrator orchestrator = orchestrator(List.of(observer));
        AgentTask task = AgentTask.begin("observer", "输入")
                .inConversation("conv-1")
                .withFeedback("重写第三段")
                .withContext(Map.of("lang", "zh"));
        AgentResult result = orchestrator.dispatch(task);
        Assertions.assertTrue(result.success(), result.error());
        Assertions.assertEquals("feedback=重写第三段,lang=zh", result.output());
    }

    @Test
    public void executorExceptionFallsBackToFail() {
        AgentExecutor broken = new AgentExecutor() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public AgentResult run(AgentTask task) {
                throw new IllegalStateException("模拟执行器内部故障");
            }
        };
        AgentOrchestrator orchestrator = orchestrator(List.of(broken));
        AgentResult result = orchestrator.dispatch("broken", "x");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(result.error().contains("执行器异常"), result.error());
    }
}
