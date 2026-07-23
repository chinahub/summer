package cn.jiebaba.summer.ai.advisor;

/** 同步顾问链：推进到下一顾问，末端顾问后即调用底层模型。 */
public interface CallAdvisorChain {

    /**
     * 推进到下游顾问处理请求，返回最终响应。
     *
     * @param request 当前请求
     * @return 处理后的响应
     */
    AdvisedResponse advanceCall(AdvisedRequest request);
}
