package cn.jiebaba.summer.ai.chat.content;

/**
 * 语音输入内容片段。data 为 base64 编码音频，format 为 wav 或 mp3。
 * 用于支持语音多模态输入的模型（如 GLM-4-Voice）。
 */
public record InputAudioPart(String data, String format) implements ContentPart {

    @Override
    public String type() {
        return "input_audio";
    }
}
