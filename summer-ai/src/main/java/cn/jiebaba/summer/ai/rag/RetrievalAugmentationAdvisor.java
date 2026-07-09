package cn.jiebaba.summer.ai.rag;

import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.chat.UserMessage;
import cn.jiebaba.summer.ai.vectorstore.RetrievalResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索增强顾问：在用户提问前按其问题检索相关资料，注入为 system 上下文，
 * 使模型回答可基于外部知识。资料为空时不改写 Prompt，保持原始调用。
 */
public class RetrievalAugmentationAdvisor {

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

    /** 仅检索并格式化上下文文本，不依赖 Prompt 结构。 */
    public String retrieveContext(String userQuery) {
        return formatContext(retriever.retrieve(userQuery));
    }

    /** 增强 Prompt：在最后一条 user 消息前插入检索到的资料 system 消息。 */
    public Prompt augment(Prompt prompt) {
        String query = lastUserText(prompt);
        if (query == null) {
            return prompt;
        }
        String context = formatContext(retriever.retrieve(query));
        if (context.isEmpty()) {
            return prompt;
        }
        List<Message> messages = new ArrayList<>(prompt.getMessages());
        int idx = lastUserIndex(messages);
        if (idx < 0) {
            return prompt;
        }
        messages.add(idx, Message.system(buildContextMessage(context)));
        return new Prompt(messages, prompt.getOptions());
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

    /** 取最后一条 UserMessage 的纯文本，找不到返回 null。 */
    private String lastUserText(Prompt prompt) {
        int idx = lastUserIndex(prompt.getMessages());
        if (idx < 0) {
            return null;
        }
        Message m = prompt.getMessages().get(idx);
        return m instanceof UserMessage ? m.content() : null;
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
