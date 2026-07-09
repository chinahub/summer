package cn.jiebaba.summer.ai.chat;

import cn.jiebaba.summer.ai.chat.content.ContentPart;
import cn.jiebaba.summer.ai.chat.content.TextPart;

import java.util.List;

/**
 * user 角色消息，支持纯文本与多模态内容片段（图片/语音）。
 * 单文本片段时 content() 直接返回该文本；多片段时返回所有文本片段拼接。
 */
public record UserMessage(List<ContentPart> parts) implements Message {

    public UserMessage {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("UserMessage 内容片段不能为空");
        }
        parts = List.copyOf(parts);
    }

    public UserMessage(String content) {
        this(List.of(new TextPart(content == null ? "" : content)));
    }

    @Override
    public String role() {
        return "user";
    }

    /** 拼接所有文本片段为纯文本，便于不带多模态能力的调用方读取。 */
    @Override
    public String content() {
        StringBuilder sb = new StringBuilder();
        for (ContentPart part : parts) {
            if (part instanceof TextPart t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    /** 仅含单个文本片段的快捷构造。 */
    public static UserMessage text(String content) {
        return new UserMessage(content);
    }

    /** 由多个内容片段构造多模态消息。 */
    public static UserMessage of(ContentPart... parts) {
        return new UserMessage(List.of(parts));
    }
}
