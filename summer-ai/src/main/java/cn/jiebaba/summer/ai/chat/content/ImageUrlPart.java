package cn.jiebaba.summer.ai.chat.content;

/**
 * 图片内容片段。url 可为 http(s) 链接，或 data:image/...;base64,... 数据 URI。
 * detail 控制精度（auto/low/high），未指定时为 null，由厂商按默认 auto 处理。
 */
public record ImageUrlPart(String url, String detail) implements ContentPart {

    public ImageUrlPart(String url) {
        this(url, null);
    }

    @Override
    public String type() {
        return "image_url";
    }
}
