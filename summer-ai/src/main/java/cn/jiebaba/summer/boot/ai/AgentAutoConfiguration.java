package cn.jiebaba.summer.boot.ai;

import cn.jiebaba.summer.ai.agent.AgentExecutor;
import cn.jiebaba.summer.ai.agent.AgentOrchestrator;
import cn.jiebaba.summer.ai.agent.AgentPolicy;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.ConditionalOnMissingBean;
import cn.jiebaba.summer.core.annotation.ConditionalOnProperty;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.env.Environment;

import java.util.ArrayList;

/**
 * summer-ai 进程内多 agent 协作自动配置：装配 {@link AgentPolicy}（协作超时与链深度上限）与
 * {@link AgentOrchestrator}（按技能名路由任务到本地 {@link AgentExecutor}，虚拟线程池执行，
 * ScopedValue 协作链防环/限深）。纯内存编排：无端口、无文件，默认装配无害。
 * <p>与 {@link AiAutoConfiguration} 同构：本类随 summer-ai 模块发布（包名仍为
 * {@code cn.jiebaba.summer.boot.ai}），由 SummerApplication 探测到 {@code AgentOrchestrator}
 * 在 classpath 后反射注册；总开关遵循 {@link AiAutoConfiguration} 的约定——
 * {@code summer.ai.enabled=false} 时整体关闭（连注册都不发生），缺省开启。
 * <p>配置项：
 * <ul>
 *   <li>{@code summer.ai.enabled}：AI 自动配置总开关（默认开启，false 关闭本配置与 AiAutoConfiguration）</li>
 *   <li>{@code summer.ai.agent.timeout-seconds}：单次任务同步等待上限（默认 300）</li>
 *   <li>{@code summer.ai.agent.max-depth}：协作链最大深度，防嵌套成环（默认 8）</li>
 * </ul>
 * 两个 bean 均有 {@code @ConditionalOnMissingBean} 退避：应用可自定义策略/编排器接管。
 */
@Configuration
@ConditionalOnProperty(name = "summer.ai.enabled", matchIfMissing = true)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentPolicy agentPolicy(Environment env) {
        return new AgentPolicy(
                env.getProperty("summer.ai.agent.timeout-seconds", Integer.class,
                        AgentPolicy.DEFAULT_TIMEOUT_SECONDS),
                env.getProperty("summer.ai.agent.max-depth", Integer.class,
                        AgentPolicy.DEFAULT_MAX_DEPTH));
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentOrchestrator agentOrchestrator(AgentPolicy policy, ApplicationContext context) {
        // 惰性求值：bean 创建期不触碰 AgentExecutor 表（执行器可能依赖编排器，直接收集会成环）
        return new AgentOrchestrator(
                () -> new ArrayList<>(context.getBeansOfType(AgentExecutor.class).values()),
                policy);
    }
}
