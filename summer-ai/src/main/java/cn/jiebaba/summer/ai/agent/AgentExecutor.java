package cn.jiebaba.summer.ai.agent;

/**
 * Agent 执行器 SPI：按 {@link #name()} 注册，执行一个协作任务。
 * 实现内部可自由组合 summer-ai 既有能力（ChatClient 对话、工具循环、RAG），也可为纯逻辑。
 * 实现为 Spring/Summer 组件或经自动装配注册后由 {@link AgentOrchestrator} 按技能名路由。
 * <p>方法名刻意避开 execute（易与 SQL 执行混淆）：这里执行的是协作任务，不是语句。
 * <p>凭证纪律：返回成功只证明执行结束，不证明任务达成——{@link AgentResult#evidence()} 应携带
 * 可核验证据（产物哈希/退出码/校验摘要等），调用方的完成判定以凭证为准，不采信执行器自述。
 */
public interface AgentExecutor {

    /** 执行器名（编排器按此路由技能），如 "writer"/"reviewer"。 */
    String name();

    /** 执行一个协作任务，返回结果（不抛异常，失败以 {@link AgentResult#fail} 表达）。 */
    AgentResult run(AgentTask task);
}
