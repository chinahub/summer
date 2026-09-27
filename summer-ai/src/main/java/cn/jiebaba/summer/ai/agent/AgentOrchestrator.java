package cn.jiebaba.summer.ai.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 进程内多 agent 协作编排器（单 JVM 内按技能路由任务到本地 {@link AgentExecutor}）：
 * <ul>
 *   <li>路由 {@link #dispatch}/{@link #dispatch(String, String)}：按技能名同步执行并等待结果
 *       （超时被 {@link AgentPolicy#timeoutSeconds()} 限制，超时取消执行并判失败）；</li>
 *   <li>异步 {@link #submit} 与并行 {@link #dispatchAll}（虚拟线程池扇出，结果按入参顺序返回）；</li>
 *   <li>防环：JDK 25 正式版 {@link ScopedValue} 携带协作链（已访问技能序列），执行前查重判环、
 *       查深度上限——嵌套 dispatch 跨虚拟线程也能感知完整链路；</li>
 *   <li>执行器表惰性解析一次（volatile + 双重检查），同名重复注册记 WARNING 并保留先注册者。</li>
 * </ul>
 * 纯内存编排：无端口、无文件、无外部依赖；执行器首次解析后缓存（之后新注册的执行器不参与路由）。
 */
public class AgentOrchestrator {

    private static final Logger LOG = Logger.getLogger(AgentOrchestrator.class.getName());

    /** 协作链（已访问技能序列）：在派发线程上求值，执行线程上经 ScopedValue 绑定传递。 */
    private static final ScopedValue<List<String>> CHAIN = ScopedValue.newInstance();

    private final Supplier<List<AgentExecutor>> executorSource;
    private final AgentPolicy policy;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    /** 执行器表：首次使用时求值（避免 bean 装配期循环依赖）。 */
    private volatile Map<String, AgentExecutor> executors;

    public AgentOrchestrator(Supplier<List<AgentExecutor>> executorSource, AgentPolicy policy) {
        this.executorSource = executorSource;
        this.policy = policy == null ? AgentPolicy.ofDefaults() : policy;
    }

    /** 便捷重载：begin 一个任务并同步派发。 */
    public AgentResult dispatch(String skill, String input) {
        return dispatch(AgentTask.begin(skill, input));
    }

    /**
     * 同步路由并等待结果。未知技能/协作回环/超过最大深度在派发线程上快速失败（不进线程池）；
     * 执行超时被取消并判失败；执行器异常被兜底为 {@link AgentResult#fail}（SPI 约定不抛，仍防御）。
     */
    public AgentResult dispatch(AgentTask task) {
        return await(submit(task), task.skill());
    }

    /** 异步派发：返回 future；快速失败（未知技能/回环/超深）时返回已完成 future。 */
    public CompletableFuture<AgentResult> submit(AgentTask task) {
        AgentExecutor executor = resolveExecutors().get(task.skill());
        if (executor == null) {
            return CompletableFuture.completedFuture(AgentResult.fail(
                    "未注册的执行器: " + task.skill() + "，已注册: " + resolveExecutors().keySet()));
        }
        List<String> chain = CHAIN.isBound() ? CHAIN.get() : List.of();
        if (chain.contains(task.skill())) {
            LOG.warning("检测到协作回环: " + chain + " -> " + task.skill());
            return CompletableFuture.completedFuture(
                    AgentResult.fail("检测到协作回环: " + chain + " -> " + task.skill()));
        }
        if (chain.size() >= policy.maxDepth()) {
            String msg = "超过最大协作深度(" + policy.maxDepth() + "): " + chain + " -> " + task.skill();
            LOG.warning(msg);
            return CompletableFuture.completedFuture(AgentResult.fail(msg));
        }
        List<String> newChain = append(chain, task.skill());
        LOG.info("路由任务 skill=" + task.skill() + " (taskId=" + task.taskId() + ", chain=" + newChain + ")");
        return CompletableFuture.supplyAsync(() -> execute(executor, task, newChain), pool);
    }

    /** 并行扇出多个任务（各自独立超时），结果严格按入参顺序返回。 */
    public List<AgentResult> dispatchAll(List<AgentTask> tasks) {
        List<CompletableFuture<AgentResult>> futures = new ArrayList<>(tasks.size());
        for (AgentTask task : tasks) {
            futures.add(submit(task));
        }
        List<AgentResult> results = new ArrayList<>(tasks.size());
        for (int i = 0; i < futures.size(); i++) {
            results.add(await(futures.get(i), tasks.get(i).skill()));
        }
        return results;
    }

    /** 已注册技能名录（诊断用，不可变快照）。 */
    public Set<String> skills() {
        return Set.copyOf(resolveExecutors().keySet());
    }

    // ---------- 内部 ----------

    /** 执行器表惰性解析（volatile + 双重检查）；同名重复注册记 WARNING 并保留先注册者。 */
    private Map<String, AgentExecutor> resolveExecutors() {
        Map<String, AgentExecutor> resolved = executors;
        if (resolved == null) {
            synchronized (this) {
                if (executors == null) {
                    Map<String, AgentExecutor> m = new LinkedHashMap<>();
                    for (AgentExecutor executor : executorSource.get()) {
                        AgentExecutor prev = m.putIfAbsent(executor.name(), executor);
                        if (prev != null) {
                            LOG.warning("执行器重名 '" + executor.name() + "'，保留先注册者 "
                                    + prev.getClass().getSimpleName() + "，忽略 "
                                    + executor.getClass().getSimpleName());
                        }
                    }
                    executors = Collections.unmodifiableMap(m);
                }
                resolved = executors;
            }
        }
        return resolved;
    }

    /** 在虚拟线程中绑定新协作链后调用执行器（SPI 不抛异常，仍做防御兜底）。 */
    private AgentResult execute(AgentExecutor executor, AgentTask task, List<String> newChain) {
        try {
            AgentResult result = ScopedValue.where(CHAIN, newChain).call(() -> executor.run(task));
            if (!result.success()) {
                LOG.warning("执行失败 skill=" + task.skill() + " (taskId=" + task.taskId() + "): "
                        + result.error());
            }
            return result;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "执行器异常 skill=" + task.skill() + " (taskId=" + task.taskId() + ")", e);
            return AgentResult.fail("执行器异常: " + e.getMessage());
        }
    }

    /** 等待结果：超时取消执行并判失败，中断恢复中断标记，执行异常兜底为失败。 */
    private AgentResult await(CompletableFuture<AgentResult> future, String skill) {
        try {
            return future.get(policy.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            String msg = "执行超时(" + policy.timeoutSeconds() + "s): " + skill;
            LOG.warning(msg);
            return AgentResult.fail(msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.fail("协作被中断: " + skill);
        } catch (ExecutionException e) {
            LOG.log(Level.WARNING, "执行器异常: " + skill, e.getCause());
            return AgentResult.fail("执行器异常: " + e.getCause().getMessage());
        }
    }

    private static List<String> append(List<String> chain, String skill) {
        List<String> next = new ArrayList<>(chain.size() + 1);
        next.addAll(chain);
        next.add(skill);
        return List.copyOf(next);
    }
}
