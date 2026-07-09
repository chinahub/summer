package cn.jiebaba.summer.ai.chat.content;

/**
 * 多模态消息内容片段抽象，对应 OpenAI content 数组中的单元素。
 * sealed 接口限定三种实现：文本、图片、语音。
 */
public sealed interface ContentPart permits TextPart, ImageUrlPart, InputAudioPart {

    /** OpenAI 片段类型标识：text/image_url/input_audio。 */
    String type();
}
