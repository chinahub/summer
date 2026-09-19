package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.agent.A2aContract;
import cn.jiebaba.summer.ai.agent.A2aCoordinator;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A2A 服务端 HTTP 端点（经 {@code A2aAutoConfiguration} 以 {@code @Bean} 注册，
 * 路由由 WebRouteRegistrar 在容器刷新后统一扫描，与业务控制器同构）。
 * <ul>
 *   <li>POST /ai/a2a/tasks：接域——对方委派任务进来，落契约回 202，虚拟线程本地执行；</li>
 *   <li>GET /ai/a2a/tasks/{requestId}：任务状态（委派方断线恢复轮询取回结果）；</li>
 *   <li>POST /ai/a2a/callback：接收对方回传结果，补全等待中的委派；</li>
 *   <li>GET /ai/a2a/contracts：本方契约台账。</li>
 * </ul>
 * 本类不发布（无独立意义），由自动装配创建；未开启 {@code summer.ai.a2a.enabled} 时不注册。
 */
@RestController
@RequestMapping("/ai/a2a")
public class A2aEndpoints {

    private final A2aCoordinator coordinator;

    public A2aEndpoints(A2aCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /** 接域：落契约即回 202 语义（返回 PENDING），任务在虚拟线程中本地执行。 */
    @PostMapping("/tasks")
    public Map<String, Object> acceptTask(@RequestBody Map<String, String> task) {
        return coordinator.accept(task);
    }

    /** 任务状态查询（委派方断线恢复时轮询）。 */
    @GetMapping("/tasks/{requestId}")
    public Map<String, Object> taskStatus(@PathVariable String requestId) {
        return coordinator.status(requestId).map(A2aEndpoints::toMap)
                .orElseGet(() -> Map.of("requestId", requestId, "status", "UNKNOWN"));
    }

    /** 对方回传委派结果。 */
    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody Map<String, String> body) {
        boolean delivered = coordinator.complete(body.get("requestId"), new AgentResult(
                Boolean.parseBoolean(body.getOrDefault("success", "false")),
                body.get("output"), body.get("evidence"), body.get("error")));
        return Map.of("received", true, "delivered", delivered);
    }

    /** 本方契约台账（诊断）。 */
    @GetMapping("/contracts")
    public List<Map<String, Object>> contracts() {
        return coordinator.contracts().stream().map(A2aEndpoints::toMap).toList();
    }

    static Map<String, Object> toMap(A2aContract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", c.requestId());
        m.put("messageId", c.messageId());
        m.put("conversationId", c.conversationId());
        m.put("traceId", c.traceId());
        m.put("direction", c.direction());
        m.put("peerUrl", c.peerUrl());
        m.put("callbackUrl", c.callbackUrl());
        m.put("skill", c.skill());
        m.put("status", c.status());
        m.put("output", c.output());
        m.put("error", c.error());
        m.put("createdAt", c.createdAt());
        m.put("updatedAt", c.updatedAt());
        return m;
    }
}
