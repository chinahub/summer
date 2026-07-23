package cn.jiebaba.summer.ai.advisor;

import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Prompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 顾问链工厂与默认实现：按 {@link Advisor#getOrder()} 升序（稳定排序）组装链，
 * 末端节点调用底层 {@link ChatModel}。
 */
public final class AdvisorChains {

    private AdvisorChains() {
    }

    /** 按顺序运行顾问链并执行同步调用。 */
    public static AdvisedResponse call(ChatModel chatModel, List<Advisor> advisors, AdvisedRequest request) {
        return new DefaultCallAdvisorChain(chatModel, ordered(advisors), 0).advanceCall(request);
    }

    /** 按顺序运行顾问链并执行流式调用。 */
    public static Stream<AdvisedResponse> stream(ChatModel chatModel, List<Advisor> advisors, AdvisedRequest request) {
        return new DefaultStreamAdvisorChain(chatModel, ordered(advisors), 0).advanceStream(request);
    }

    /** 稳定排序：按 order 升序，同序保持添加顺序。 */
    private static List<Advisor> ordered(List<Advisor> advisors) {
        List<Advisor> copy = new ArrayList<>(advisors);
        copy.sort(Comparator.comparingInt(Advisor::getOrder));
        return copy;
    }

    /** 同步链默认实现：按索引递归推进，末端调用模型。 */
    private static final class DefaultCallAdvisorChain implements CallAdvisorChain {
        private final ChatModel chatModel;
        private final List<Advisor> advisors;
        private final int index;

        DefaultCallAdvisorChain(ChatModel chatModel, List<Advisor> advisors, int index) {
            this.chatModel = chatModel;
            this.advisors = advisors;
            this.index = index;
        }

        @Override
        public AdvisedResponse advanceCall(AdvisedRequest request) {
            if (index < advisors.size()) {
                Advisor advisor = advisors.get(index);
                CallAdvisorChain next = new DefaultCallAdvisorChain(chatModel, advisors, index + 1);
                return advisor.adviseCall(request, next);
            }
            ChatResponse response = chatModel.call(new Prompt(request.getMessages(), request.getOptions()));
            return new AdvisedResponse(response, request.getContext());
        }
    }

    /** 流式链默认实现：按索引递归推进，末端调用模型流并包装为 AdvisedResponse。 */
    private static final class DefaultStreamAdvisorChain implements StreamAdvisorChain {
        private final ChatModel chatModel;
        private final List<Advisor> advisors;
        private final int index;

        DefaultStreamAdvisorChain(ChatModel chatModel, List<Advisor> advisors, int index) {
            this.chatModel = chatModel;
            this.advisors = advisors;
            this.index = index;
        }

        @Override
        public Stream<AdvisedResponse> advanceStream(AdvisedRequest request) {
            if (index < advisors.size()) {
                Advisor advisor = advisors.get(index);
                StreamAdvisorChain next = new DefaultStreamAdvisorChain(chatModel, advisors, index + 1);
                return advisor.adviseStream(request, next);
            }
            Map<String, Object> context = request.getContext();
            return chatModel.stream(new Prompt(request.getMessages(), request.getOptions()))
                    .map(chunk -> new AdvisedResponse(chunk, context));
        }
    }
}
