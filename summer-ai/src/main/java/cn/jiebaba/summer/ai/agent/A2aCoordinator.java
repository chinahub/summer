package cn.jiebaba.summer.ai.agent;

import cn.jiebaba.summer.core.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A2A 跨实例协作协调器（Agent→Agent 的双向枢纽）：
 * <ul>
 *   <li>出域 {@link #delegate}：落契约 → POST 对方任务端点 → 同步等待回调（超时兜底）；</li>
 *   <li>入域 {@link #accept}：落契约 → 虚拟线程用本地执行器执行（结构性防环：执行表排除
 *       a2a 网关自身，接域任务不得再委派）→ 回调对方；</li>
 *   <li>恢复 {@link #recoverInbound}/{@link #pollPeer}：入域重启后重执行未完成任务；
 *       出域凭契约向对方轮询——回调丢失不丢结果（契约即责任凭证）。</li>
 * </ul>
 * 纯 JDK（HttpClient + 虚拟线程），不依赖 Web 层能力，可脱离容器单独使用（仅需 summer-boot 的 core 基础）。
 */
public class A2aCoordinator {

    private static final Logger LOG = Logger.getLogger(A2aCoordinator.class.getName());

    private final ContractStore store;
    /** 本地执行器表：首次使用时求值并排除网关自身（避免 bean 创建期循环依赖 + 防环）。 */
    private final Supplier<List<AgentExecutor>> executorSource;
    private volatile Map<String, AgentExecutor> executors;
    private final HttpClient http = HttpClient.newHttpClient();
    /** 出域回调等待表：requestId → future（内存态；进程重启后由恢复流程轮询补全）。 */
    private final Map<String, CompletableFuture<AgentResult>> pendingCallbacks = new ConcurrentHashMap<>();
    private final ExecutorService inboundPool = Executors.newVirtualThreadPerTaskExecutor();
    private final DelegationPolicy policy;
    private final String callbackUrl;

    public A2aCoordinator(ContractStore store, Supplier<List<AgentExecutor>> executorSource,
                          DelegationPolicy policy, String callbackUrl) {
        this.store = store;
        this.executorSource = executorSource;
        this.policy = policy == null ? DelegationPolicy.ofDefaults() : policy;
        this.callbackUrl = normalize(callbackUrl);
    }

    // ---------- 出域：委派 ----------

    /**
     * 把任务委派给对端实例并同步等待结果。peerUrl 为对端基地址（如 http://host:port），
     * 对端需暴露 POST /ai/a2a/tasks（接域）与 POST /ai/a2a/callback（回传）。
     */
    public AgentResult delegate(AgentTask task, String peerUrl) {
        String peer = normalize(peerUrl);
        if (peer == null) {
            return AgentResult.fail("未配置 A2A 对端地址（peerUrl）");
        }
        A2aContract contract = newContract(task, peer);
        store.save(contract);
        LOG.info("A2A 委派 skill=" + contract.skill() + " → " + peer + " (requestId=" + contract.requestId() + ")");

        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        pendingCallbacks.put(contract.requestId(), future);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(peer + "/ai/a2a/tasks"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJsonStr(taskEnvelope(contract))))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return giveUp(contract, "对端接收失败 HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            return giveUp(contract, "委派发送失败: " + e.getMessage());
        }
        try {
            AgentResult result = future.get(policy.timeoutSeconds(), TimeUnit.SECONDS);
            store.save(withStatus(contract, result.success() ? A2aContract.ST_COMPLETED : A2aContract.ST_FAILED,
                    result));
            return result.success() ? result : AgentResult.fail(result.error());
        } catch (TimeoutException e) {
            return giveUp(contract, "委派超时(" + policy.timeoutSeconds() + "s)");
        } catch (Exception e) {
            return giveUp(contract, "委派失败: " + e.getMessage());
        }
    }

    /** 对方回传结果：补全等待中的委派（无等待方也落契约，恢复流程可查）。 */
    public boolean complete(String requestId, AgentResult result) {
        CompletableFuture<AgentResult> future = pendingCallbacks.remove(requestId);
        A2aContract contract = store.find(requestId).orElse(null);
        if (contract != null) {
            store.save(withStatus(contract, result.success() ? A2aContract.ST_COMPLETED : A2aContract.ST_FAILED,
                    result));
        }
        if (future == null) {
            return false;
        }
        future.complete(result);
        return true;
    }

    // ---------- 入域：接域 ----------

    /**
     * 接收对方委派：落契约即返回（HTTP 层可立即 202），虚拟线程用本地执行器执行后回调对方。
     *
     * @param raw HTTP 层解析出的任务字段（requestId/skill/input/feedback/context/callbackUrl 等）
     */
    public Map<String, Object> accept(Map<String, String> raw) {
        String requestId = raw.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        AgentTask task = new AgentTask(requestId,
                orBlank(raw.get("messageId")), orBlank(raw.get("conversationId")), orBlank(raw.get("traceId")),
                orElse(raw.get("skill"), "task"), orBlank(raw.get("input")),
                raw.get("feedback"), raw.get("context"), raw.get("callbackUrl"));
        A2aContract contract = newContract(task, null);
        store.save(contract);
        LOG.info("A2A 接域 skill=" + contract.skill() + " (requestId=" + requestId + " from="
                + contract.callbackUrl() + ")");
        inboundPool.submit(() -> executeInbound(contract));
        return Map.of("requestId", requestId, "status", A2aContract.ST_PENDING);
    }

    /** 本地执行入域任务并回调对方（回调失败不落败，对方可凭 requestId 轮询取回）。 */
    private void executeInbound(A2aContract contract) {
        store.save(withStatus(contract, A2aContract.ST_RUNNING, null));
        AgentResult result;
        try {
            AgentExecutor executor = resolveLocalExecutor();
            if (executor == null) {
                result = AgentResult.fail("本地执行器不存在: "
                        + (policy.localExecutor().isEmpty() ? "(未注册任何 AgentExecutor)" : policy.localExecutor()));
            } else {
                AgentTask task = new AgentTask(contract.requestId(), contract.messageId(),
                        contract.conversationId(), contract.traceId(), contract.skill(), contract.input(),
                        contract.feedback(), contract.context(), contract.callbackUrl());
                result = executor.run(task);
            }
        } catch (RuntimeException e) {
            result = AgentResult.fail("入域执行异常: " + e.getMessage());
        }
        store.save(withStatus(contract, result.success() ? A2aContract.ST_COMPLETED : A2aContract.ST_FAILED,
                result));
        callbackPeer(contract.callbackUrl(), contract.requestId(), result);
    }

    private void callbackPeer(String peerCallbackUrl, String requestId, AgentResult result) {
        String base = normalize(peerCallbackUrl);
        if (base == null) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requestId", requestId);
            body.put("success", String.valueOf(result.success()));
            body.put("output", result.output());
            body.put("evidence", result.evidence());
            body.put("error", result.error());
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/ai/a2a/callback"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJsonStr(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.info("A2A 回调 " + requestId + " → " + base + " HTTP " + response.statusCode());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "A2A 回调失败（对方恢复时可凭 requestId 轮询取回）: " + requestId, e);
        }
    }

    // ---------- 恢复 ----------

    /** 出域恢复：向对方轮询在途任务状态。COMPLETED/FAILED 返回结果，对端不可达或在途返回空。 */
    public Optional<AgentResult> pollPeer(A2aContract contract) {
        String peer = normalize(contract.peerUrl());
        if (peer == null) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(peer + "/ai/a2a/tasks/" + contract.requestId()))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonUtil.JSONObject o = JsonUtil.parseObj(response.body());
            String status = o.getStr("status");
            if (A2aContract.ST_COMPLETED.equals(status)) {
                return Optional.of(AgentResult.ok(o.getStr("output"), "a2a-polled"));
            }
            if (A2aContract.ST_FAILED.equals(status)) {
                return Optional.of(AgentResult.fail(o.getStr("error")));
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.fine("A2A 轮询失败 " + contract.requestId() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** 入域恢复：重启后重新执行未完成的接域任务，返回重执行数量。 */
    public int recoverInbound() {
        List<A2aContract> actives = store.listInboundActive();
        for (A2aContract contract : actives) {
            inboundPool.submit(() -> executeInbound(contract));
        }
        return actives.size();
    }

    /** 当前在途出域契约（恢复扫描用）。 */
    public List<A2aContract> outboundActive() {
        return store.listOutboundActive();
    }

    public Optional<A2aContract> status(String requestId) {
        return store.find(requestId);
    }

    public List<A2aContract> contracts() {
        return store.list();
    }

    // ---------- 内部 ----------

    private A2aContract newContract(AgentTask task, String peerUrl) {
        String now = now();
        return new A2aContract(task.requestId(), task.messageId(), task.conversationId(), task.traceId(),
                peerUrl == null ? A2aContract.DIR_INBOUND : A2aContract.DIR_OUTBOUND,
                peerUrl, task.callbackUrl(), task.skill(), task.input(), task.feedback(), task.context(),
                A2aContract.ST_PENDING, null, null, null, now, now);
    }

    private Map<String, Object> taskEnvelope(A2aContract contract) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", contract.requestId());
        body.put("messageId", contract.messageId());
        body.put("conversationId", contract.conversationId());
        body.put("traceId", contract.traceId());
        body.put("callbackUrl", callbackUrl);
        body.put("skill", contract.skill());
        body.put("input", contract.input());
        body.put("feedback", contract.feedback());
        body.put("context", contract.context());
        return body;
    }

    /** 接域执行器解析（惰性 + 排除 a2a 网关自身：结构性防环）。 */
    private AgentExecutor resolveLocalExecutor() {
        Map<String, AgentExecutor> resolved = executors;
        if (resolved == null) {
            synchronized (this) {
                if (executors == null) {
                    Map<String, AgentExecutor> m = new LinkedHashMap<>();
                    for (AgentExecutor executor : executorSource.get()) {
                        if (!(executor instanceof A2aGateway)) {
                            m.put(executor.name(), executor);
                        }
                    }
                    executors = m;
                }
                resolved = executors;
            }
        }
        if (resolved.isEmpty()) {
            return null;
        }
        return policy.localExecutor().isEmpty()
                ? resolved.values().iterator().next()
                : resolved.get(policy.localExecutor());
    }

    private AgentResult giveUp(A2aContract contract, String error) {
        pendingCallbacks.remove(contract.requestId());
        store.save(withStatus(contract, A2aContract.ST_FAILED, AgentResult.fail(error)));
        return AgentResult.fail("A2A " + error + "，可重做");
    }

    private static A2aContract withStatus(A2aContract c, String status, AgentResult result) {
        return new A2aContract(c.requestId(), c.messageId(), c.conversationId(), c.traceId(),
                c.direction(), c.peerUrl(), c.callbackUrl(), c.skill(), c.input(), c.feedback(), c.context(),
                status,
                result == null ? null : result.output(),
                result == null ? null : result.evidence(),
                result == null ? null : result.error(),
                c.createdAt(), now());
    }

    private static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String now() {
        return java.time.LocalDateTime.now().toString();
    }

    private static String orBlank(String s) {
        return s == null ? "" : s;
    }

    private static String orElse(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    /** 供信封测试/诊断：生成全新任务 id 组。 */
    static String randomId() {
        return UUID.randomUUID().toString();
    }
}
