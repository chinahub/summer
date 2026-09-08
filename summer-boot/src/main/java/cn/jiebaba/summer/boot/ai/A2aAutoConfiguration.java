package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.agent.A2aCoordinator;
import cn.jiebaba.summer.ai.agent.A2aGateway;
import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.ContractStore;
import cn.jiebaba.summer.ai.agent.DelegationPolicy;
import cn.jiebaba.summer.ai.agent.FileContractStore;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.ConditionalOnProperty;
import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.env.Environment;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * summer-ai A2A 跨实例协作自动配置：按 {@code summer.ai.a2a.*} 装配契约存储、协调器与网关执行器，
 * 并注册服务端 HTTP 端点（POST /ai/a2a/tasks、GET /ai/a2a/tasks/{id}、POST /ai/a2a/callback、
 * GET /ai/a2a/contracts）。与 {@link AiAutoConfiguration} 同构：本类位于 summer-boot，编译期引用
 * summer-ai（optional），由 SummerApplication 在探测到 summer-ai 的 A2A 类后注册加载；
 * 且整体以 {@code summer.ai.a2a.enabled=true} 显式开启（opt-in，默认关闭零影响）。
 * <p>配置项：
 * <ul>
 *   <li>{@code summer.ai.a2a.enabled}：true 开启（默认关闭）</li>
 *   <li>{@code summer.ai.a2a.peer-url}：对端实例基地址（出域委派目标，可被业务在任务级覆盖）</li>
 *   <li>{@code summer.ai.a2a.callback-url}：本方回调基地址（缺省 http://127.0.0.1:{server.port}）</li>
 *   <li>{@code summer.ai.a2a.timeout-seconds}：出域同步等待上限（默认 300）</li>
 *   <li>{@code summer.ai.a2a.workdir}：契约文件目录（默认 ./data/a2a）</li>
 *   <li>{@code summer.ai.a2a.local-executor}：接域任务的本地执行器名（缺省取第一个注册的
 *       AgentExecutor；接域执行表固定排除 a2a 网关自身，防再委派成环）</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "summer.ai.a2a.enabled", havingValue = "true")
public class A2aAutoConfiguration {

    @Bean
    public ContractStore a2aContractStore(Environment env) {
        return new FileContractStore(
                Path.of(env.getProperty("summer.ai.a2a.workdir", String.class, "./data/a2a")));
    }

    @Bean
    public DelegationPolicy a2aDelegationPolicy(Environment env) {
        return new DelegationPolicy(
                env.getProperty("summer.ai.a2a.timeout-seconds", Integer.class,
                        DelegationPolicy.DEFAULT_TIMEOUT_SECONDS),
                env.getProperty("summer.ai.a2a.local-executor", String.class, ""));
    }

    @Bean
    public A2aCoordinator a2aCoordinator(ContractStore store, DelegationPolicy policy,
                                         ApplicationContext context, Environment env) {
        String callbackUrl = env.getProperty("summer.ai.a2a.callback-url", String.class, "");
        if (callbackUrl.isBlank()) {
            int port = env.getProperty("server.port", Integer.class, 8080);
            callbackUrl = "http://127.0.0.1:" + port;
        }
        // 惰性求值：bean 创建期不触碰 AgentExecutor 表（A2aGateway 依赖本协调器，直接收集会成环）
        return new A2aCoordinator(store,
                () -> new ArrayList<>(context.getBeansOfType(AgentExecutor.class).values()),
                policy, callbackUrl);
    }

    @Bean
    public A2aGateway a2aGateway(A2aCoordinator coordinator, Environment env) {
        return new A2aGateway(coordinator, env.getProperty("summer.ai.a2a.peer-url", String.class, ""));
    }

    @Bean
    public A2aEndpoints a2aEndpoints(A2aCoordinator coordinator) {
        return new A2aEndpoints(coordinator);
    }
}
