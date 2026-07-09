package cn.jiebaba.summer.ai.chat.content;

/** 文本内容片段，最常见的消息内容形式。 */
public record TextPart(String text) implements ContentPart {

    @Override
    public String type() {
        return "text";
    }
}
