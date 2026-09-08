package cn.jiebaba.summer.ai.agent;

/**
 * Agent 执行器 SPI：按 {@link #name()} 注册，执行一个委派任务。
 * 实现内部可自由组合 summer-ai 既有能力（ChatClient 对话、工具循环、RAG），也可为纯逻辑。
 * 实现为 Spring/Summer 组件或经自动装配注册后由 {@link A2aCoordinator} 解析。
 * <p>方法名刻意避开 execute（易与 SQL 执行混淆）：这里执行的是委派任务，不是语句。
 */
public interface AgentExecutor {

    /** 执行器名（对端按此路由技能），如 "review"/"translate"/"a2a"。 */
    String name();

    /** 执行一个委派任务，返回结果（不抛异常，失败以 {@link AgentResult#fail} 表达）。 */
    AgentResult run(AgentTask task);
}
