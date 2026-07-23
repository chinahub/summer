package cn.jiebaba.summer.ai.structured;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.core.util.JsonUtil;

import java.lang.reflect.Type;

/**
 * 将模型输出转换为指定 Java Bean/Record 的转换器：构造时生成 JSON Schema 与格式提示，
 * 解析时剥离可能存在的 Markdown 代码围栏后反序列化为目标类型。
 *
 * @param <T> 目标 Java 类型
 */
public class BeanOutputConverter<T> implements OutputConverter<T> {

    private final Type type;
    private final String format;

    /**
     * 按目标类型构造转换器，预生成 JSON Schema 与格式提示。
     *
     * @param type 目标 Java 类型
     */
    public BeanOutputConverter(Type type) {
        this.type = type;
        String schema = JsonSchemaGenerator.generate(type);
        this.format = buildFormat(schema);
    }

    @Override
    public String getFormat() {
        return format;
    }

    /**
     * 将模型返回文本转换为目标类型：先剥离 Markdown 围栏与多余文字，再反序列化。
     *
     * @param text 模型输出文本
     * @return 目标类型实例
     */
    @Override
    public T convert(String text) {
        if (text == null || text.isBlank()) {
            throw new AiException("结构化输出为空，无法转换为 " + type);
        }
        String json = extractJson(text);
        try {
            return JsonUtil.toBean(json, type);
        } catch (RuntimeException e) {
            throw new AiException("无法将模型输出解析为 " + type + "：" + json, e);
        }
    }

    /**
     * 拼装格式提示：要求模型仅返回符合给定 JSON Schema 的纯 JSON，不带解释与代码围栏。
     */
    private static String buildFormat(String schema) {
        return "你的输出必须是 JSON 格式。"
                + "不要包含任何解释文字、Markdown 代码块或多余内容，"
                + "仅返回符合以下 JSON Schema 的纯 JSON：\n"
                + schema;
    }

    /**
     * 从模型输出中提取 JSON：优先剥离 Markdown 代码围栏，否则截取首个 JSON 边界之间的内容。
     */
    private static String extractJson(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            s = start >= 0 ? s.substring(start + 1) : "";
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
            return s.trim();
        }
        int begin = jsonStartIndex(s);
        if (begin > 0) {
            int finish = jsonEndIndex(s);
            if (finish > begin) {
                return s.substring(begin, finish + 1);
            }
        }
        return s;
    }

    /** 查找首个 JSON 起始边界字符（'{' 或 '['）的位置，未找到返回 -1。 */
    private static int jsonStartIndex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                return i;
            }
        }
        return -1;
    }

    /** 查找末个 JSON 结束边界字符（'}' 或 ']'）的位置，未找到返回 -1。 */
    private static int jsonEndIndex(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}' || c == ']') {
                return i;
            }
        }
        return -1;
    }
}
