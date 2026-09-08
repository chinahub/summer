package cn.jiebaba.summer.ai.agent;

/**
 * A2A 网关执行器：以 {@link AgentExecutor} 形态把"委派给对端"暴露为普通技能。
 * 模板/业务按技能名 "a2a" 绑定即可跨实例委派；对端地址取 defaultPeerUrl（配置）。
 * <p>接域侧解析本地执行器时固定排除本类型——接域任务不得再委派（结构性防环）。
 */
public class A2aGateway implements AgentExecutor {

    public static final String NAME = "a2a";

    private final A2aCoordinator coordinator;
    private final String defaultPeerUrl;

    public A2aGateway(A2aCoordinator coordinator, String defaultPeerUrl) {
        this.coordinator = coordinator;
        this.defaultPeerUrl = defaultPeerUrl;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AgentResult run(AgentTask task) {
        return coordinator.delegate(task, defaultPeerUrl);
    }
}
