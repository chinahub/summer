package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.agent.A2aContract;
import cn.jiebaba.summer.ai.agent.A2aCoordinator;
import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.AgentResult;
import cn.jiebaba.summer.ai.agent.AgentTask;
import cn.jiebaba.summer.ai.agent.ContractStore;
import cn.jiebaba.summer.ai.agent.DelegationPolicy;
import cn.jiebaba.summer.ai.agent.FileContractStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A2A 协调器全链路：出域委派→对端接收→回调补全、对端宕机失败、
 * 入域接域本地执行并回调对方、防环排除、入域恢复。
 */
public class A2aCoordinatorTest {

    private Path storeDir;
    private HttpServer fakePeer;

    @BeforeEach
    void setUp() throws IOException {
        storeDir = Files.createTempDirectory("a2a-coordinator");
    }

    @AfterEach
    void tearDown() {
        if (fakePeer != null) {
            fakePeer.stop(0);
        }
    }

    private A2aCoordinator newCoordinator(ContractStore store, AgentExecutor localExecutor, int timeoutSeconds) {
        List<AgentExecutor> all = localExecutor == null ? List.of() : List.of(localExecutor);
        return new A2aCoordinator(store, () -> all,
                new DelegationPolicy(timeoutSeconds, ""), "http://127.0.0.1:65500");
    }

    private static AgentTask task() {
        return AgentTask.begin("review", "审查一段代码").withContext("上游上下文");
    }

    /** 本地桩执行器：执行器表排除网关的对照样本。 */
    static class StubExecutor implements AgentExecutor {
        @Override public String name() { return "stub"; }
        @Override public AgentResult run(AgentTask t) {
            return AgentResult.ok("# 评审报告\n" + t.skill() + "（本地完成，输入=" + t.input() + "）", "stub: exit=0");
        }
    }

    @Test
    public void delegateThenCallbackCompletes() throws IOException {
        A2aCoordinator coordinator = newCoordinator(new FileContractStore(storeDir), new StubExecutor(), 10);
        AtomicReference<String> receivedRequestId = new AtomicReference<>();

        fakePeer = HttpServer.create(new InetSocketAddress(0), 0);
        fakePeer.createContext("/ai/a2a/tasks", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedRequestId.set(substringBetween(body, "\"requestId\":\"", "\""));
            byte[] resp = "{\"requestId\":\"x\",\"status\":\"PENDING\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
            // 异步回调补全（模拟对端完成后回传）
            new Thread(() -> coordinator.complete(receivedRequestId.get(),
                    AgentResult.ok("# 评审报告\n通过", "peer: exit=0"))).start();
        });
        fakePeer.start();
        int port = fakePeer.getAddress().getPort();

        AgentResult result = coordinator.delegate(task(), "http://127.0.0.1:" + port);

        Assertions.assertTrue(result.success(), result.error());
        Assertions.assertNotNull(receivedRequestId.get(), "对端应收到 requestId");
        Assertions.assertEquals(A2aContract.ST_COMPLETED,
                coordinator.status(receivedRequestId.get()).orElseThrow().status());
    }

    @Test
    public void delegateToDownPeerFails() throws IOException {
        A2aCoordinator coordinator = newCoordinator(new FileContractStore(storeDir), new StubExecutor(), 5);
        AgentResult result = coordinator.delegate(task(), "http://127.0.0.1:1");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(coordinator.outboundActive().isEmpty(), "FAILED 契约不在活性列表");
        Assertions.assertEquals(1, coordinator.contracts().size());
    }

    @Test
    public void missingPeerFailsFast() {
        A2aCoordinator coordinator = newCoordinator(new FileContractStore(storeDir), new StubExecutor(), 5);
        AgentResult result = coordinator.delegate(task(), "  ");
        Assertions.assertFalse(result.success());
        Assertions.assertTrue(result.error().contains("未配置 A2A 对端地址"));
    }

    @Test
    public void inboundExecutesLocallyAndCallbacks() throws IOException, InterruptedException {
        AtomicReference<String> callbackBody = new AtomicReference<>();
        fakePeer = HttpServer.create(new InetSocketAddress(0), 0);   // 模拟委派方的回调接收端
        fakePeer.createContext("/ai/a2a/callback", exchange -> {
            callbackBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("ok".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        fakePeer.start();

        A2aCoordinator coordinator = newCoordinator(new FileContractStore(storeDir), new StubExecutor(), 5);
        coordinator.accept(Map.of(
                "requestId", "peer-req-1",
                "callbackUrl", "http://127.0.0.1:" + fakePeer.getAddress().getPort(),
                "skill", "review",
                "input", "审查一段代码",
                "context", "上游上下文"));

        for (int i = 0; i < 100 && callbackBody.get() == null; i++) {
            Thread.sleep(50);
        }
        Assertions.assertNotNull(callbackBody.get(), "应在超时前回调对方");
        Assertions.assertTrue(callbackBody.get().contains("\"success\":\"true\""));
        Assertions.assertTrue(callbackBody.get().contains("评审报告"));
        Assertions.assertEquals(A2aContract.ST_COMPLETED,
                coordinator.status("peer-req-1").orElseThrow().status());
    }

    @Test
    public void recoverInboundReexecutesPendingTasks() throws IOException, InterruptedException {
        A2aCoordinator coordinator = newCoordinator(new FileContractStore(storeDir), new StubExecutor(), 5);
        // 预置一条未完成的入域契约（模拟重启前遗留）
        coordinator.accept(Map.of(
                "requestId", "peer-req-2",
                "skill", "review", "input", "恢复执行",
                "callbackUrl", "http://127.0.0.1:1"   // 回调指向黑洞，验证"回调失败不落败"
        ));
        for (int i = 0; i < 100 && !"COMPLETED".equals(
                coordinator.status("peer-req-2").orElseThrow().status()); i++) {
            Thread.sleep(20);
        }
        // 模拟重启遗留：重置为 PENDING 后触发恢复
        A2aContract c = coordinator.status("peer-req-2").orElseThrow();
        store().save(withStatus(c));
        int reexecuted = coordinator.recoverInbound();
        Assertions.assertEquals(1, reexecuted);
        for (int i = 0; i < 100 && !"COMPLETED".equals(
                coordinator.status("peer-req-2").orElseThrow().status()); i++) {
            Thread.sleep(20);
        }
        Assertions.assertEquals(A2aContract.ST_COMPLETED,
                coordinator.status("peer-req-2").orElseThrow().status());
    }

    private ContractStore store() {
        return new FileContractStore(storeDir);
    }

    private static A2aContract withStatus(A2aContract c) {
        return new A2aContract(c.requestId(), c.messageId(), c.conversationId(), c.traceId(),
                c.direction(), c.peerUrl(), c.callbackUrl(), c.skill(), c.input(), c.feedback(), c.context(),
                A2aContract.ST_PENDING, null, null, null, c.createdAt(), c.updatedAt());
    }

    private static String substringBetween(String text, String open, String close) {
        int start = text.indexOf(open);
        if (start < 0) {
            return null;
        }
        int from = start + open.length();
        int end = text.indexOf(close, from);
        return end < 0 ? null : text.substring(from, end);
    }
}
