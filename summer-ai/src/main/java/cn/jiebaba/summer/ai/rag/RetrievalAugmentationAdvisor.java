package cn.jiebaba.summer.ai.rag;

import cn.jiebaba.summer.ai.advisor.AdvisedRequest;
import cn.jiebaba.summer.ai.advisor.Advisor;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.chat.UserMessage;
import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索增强顾问：在用户提问前按其问题检索相关资料，注入为 system 上下文，
 * 使模型回答可基于外部知识。资料为空时不改写请求，保持原始调用。
 *
 * <p>实现 {@link Advisor}，可直接加入 ChatClient 顾问链；同时保留 {@link #augment(Prompt)}
 * 供 RagClient 等门面单独调用。
 */
public class RetrievalAugmentationAdvisor implements Advisor {

    /** 检索增强顾问默认顺序：在记忆顾问之后、模型调用之前。 */
    public static final int ORDER = 200;

    private static final String DEFAULT_INSTRUCTION =
            "请根据以下参考资料回答用户问题；若资料不足以回答，可结合自身知识并说明。";

    private final Retriever retriever;
    private final String instruction;

    public RetrievalAugmentationAdvisor(Retriever retriever) {
        this(retriever, DEFAULT_INSTRUCTION);
    }

    public RetrievalAugmentationAdvisor(Retriever retriever, String instruction) {
        this.retriever = retriever;
        this.instruction = instruction == null || instruction.isBlank() ? DEFAULT_INSTRUCTION : instruction;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** 仅检索并格式化上下文文本，不依赖 Prompt 结构。 */
    public String retrieveContext(String userQuery) {
        return formatContext(retriever.retrieve(userQuery));
    }

    /**
     * 请求前置：按最后一条 user 消息检索资料，在其前插入 system 上下文；无资料或无 user 消息则原样返回。
     */
    @Override
    public AdvisedRequest beforeCall(AdvisedRequest request) {
        List<Message> augmented = augmentMessages(request.getMessages());
        return augmented == null ? request : request.withMessages(augmented);
    }

    /** 增强 Prompt：在最后一条 user 消息前插入检索到的资料 system 消息。 */
    public Prompt augment(Prompt prompt) {
        List<Message> augmented = augmentMessages(prompt.getMessages());
        return augmented == null ? prompt : new Prompt(augmented, prompt.getOptions());
    }

    /**
     * 抽取检索增强逻辑：按最后一条 user 消息检索资料，在其前插入 system 上下文。
     * 无 user 消息、无资料或无结果时返回 null 表示不改写。
     */
    private List<Message> augmentMessages(List<Message> messages) {
        int idx = lastUserIndex(messages);
        if (idx < 0) {
            return null;
        }
        Message userMsg = messages.get(idx);
        if (!(userMsg instanceof UserMessage)) {
            return null;
        }
        String context = formatContext(retriever.retrieve(userMsg.content()));
        if (context.isEmpty()) {
            return null;
        }
        List<Message> augmented = new ArrayList<>(messages);
        augmented.add(idx, Message.system(buildContextMessage(context)));
        return augmented;
    }

    /** 将检索结果格式化为编号资料文本；无结果返回空串。 */
    private String formatContext(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RetrievalResult r : results) {
            sb.append("[").append(i++).append("] ").append(r.document().content()).append("\n");
        }
        return sb.toString().strip();
    }

    /** 拼接指令与资料为 system 消息内容。 */
    private String buildContextMessage(String context) {
        return instruction + "\n\n参考资料：\n" + context;
    }

    /** 取消息列表中最后一条 user 角色消息的索引。 */
    private int lastUserIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return i;
            }
        }
        return -1;
    }
}
