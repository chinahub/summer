package cn.jiebaba.summer.ai.advisor;

import java.util.stream.Stream;

/**
 * 顾问链节点：以"请求前置 + 推进下游 + 响应后置"的方式参与一次对话调用的拦截与改写。
 * 多个顾问按 {@link #getOrder()} 升序组成链，数值越小越靠近调用方（越先处理请求、越后处理响应）。
 *
 * <p>典型用法：仅覆写 {@link #beforeCall(AdvisedRequest)} 改写请求（如注入检索上下文），
 * 或再覆写 {@link #afterCall(AdvisedResponse)} 处理响应（如回存记忆）。
 * 默认实现已将前置/后置钩子接入同步与流式调用，故多数顾问无需直接实现 adviseCall/adviseStream；
 * 仅当流式需要特殊后置（如逐 token 收集后回存）时才覆写 {@link #adviseStream}。
 */
public interface Advisor {

    /** 顾问名称，默认取类名，用于日志与上下文标识。 */
    default String getName() {
        return getClass().getSimpleName();
    }

    /** 执行顺序，数值越小越先处理请求；同序按添加顺序。默认最大值（靠后）。 */
    default int getOrder() {
        return Integer.MAX_VALUE;
    }

    /**
     * 请求前置钩子：可改写请求（注入上下文、载入记忆等）后返回，默认原样返回。
     *
     * @param request 当前请求
     * @return （可能改写后的）请求
     */
    default AdvisedRequest beforeCall(AdvisedRequest request) {
        return request;
    }

    /**
     * 响应后置钩子：可改写响应（回存记忆、脱敏等）后返回，默认原样返回。
     *
     * @param response 当前响应
     * @return （可能改写后的）响应
     */
    default AdvisedResponse afterCall(AdvisedResponse response) {
        return response;
    }

    /**
     * 同步调用：前置改写请求 -> 推进下游链 -> 后置处理响应。
     *
     * @param request 当前请求
     * @param chain   下游顾问链
     * @return 处理后的响应
     */
    default AdvisedResponse adviseCall(AdvisedRequest request, CallAdvisorChain chain) {
        AdvisedResponse response = chain.advanceCall(beforeCall(request));
        return afterCall(response);
    }

    /**
     * 流式调用：前置改写请求后透传下游流；需流式后置处理的顾问可覆写此方法。
     *
     * @param request 当前请求
     * @param chain   下游顾问链
     * @return 响应片段流
     */
    default Stream<AdvisedResponse> adviseStream(AdvisedRequest request, StreamAdvisorChain chain) {
        return chain.advanceStream(beforeCall(request));
    }
}
