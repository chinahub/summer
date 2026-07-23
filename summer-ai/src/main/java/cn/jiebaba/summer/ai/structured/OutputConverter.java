package cn.jiebaba.summer.ai.structured;

/**
 * 结构化输出转换器抽象：将模型返回的文本响应转换为指定 Java 类型。
 * 实现需提供格式提示（getFormat）与解析逻辑（convert）。
 *
 * @param <T> 目标 Java 类型
 */
public interface OutputConverter<T> {

    /**
     * 返回追加到系统消息中的格式说明，指导模型按目标结构输出。
     *
     * @return 格式提示文本
     */
    String getFormat();

    /**
     * 将模型返回的原始文本转换为目标类型实例。
     *
     * @param text 模型输出文本
     * @return 转换后的目标类型实例
     */
    T convert(String text);
}
