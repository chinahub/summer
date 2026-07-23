package cn.jiebaba.summer.ai.advisor;

import java.util.stream.Stream;

/** 流式顾问链：推进到下一顾问，末端顾问后即调用底层模型流。 */
public interface StreamAdvisorChain {

    /**
     * 推进到下游顾问处理请求，返回响应片段流。
     *
     * @param request 当前请求
     * @return 响应片段流
     */
    Stream<AdvisedResponse> advanceStream(AdvisedRequest request);
}
