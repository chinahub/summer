package cn.jiebaba.summer.ai.tools;

import cn.jiebaba.summer.ai.chat.ToolDefinition;
import cn.jiebaba.summer.core.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 函数式工具实现：由参数描述生成 JSON Schema，执行体接收参数 Map 返回任意结果（自动序列化为 JSON）。
 * 这是注册工具的推荐方式，无需反射、参数名可控。
 */
public final class Tool implements ToolCallback {

    private final ToolDefinition definition;
    private final Function<Map<String, Object>, Object> executor;

    public Tool(ToolDefinition definition, Function<Map<String, Object>, Object> executor) {
        this.definition = definition;
        this.executor = executor;
    }

    public Tool(String name, String description, List<ToolParameter> parameters,
                Function<Map<String, Object>, Object> executor) {
        this(new ToolDefinition(name, description, buildSchema(parameters)), executor);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    /** 解析 JSON 参数串为 Map 交给执行体，结果序列化为 JSON；异常返回错误 JSON。 */
    @Override
    public String call(String arguments) {
        try {
            Object parsed = JsonUtil.parse(arguments == null || arguments.isBlank() ? "{}" : arguments);
            Map<String, Object> args = parsed instanceof Map<?, ?> m ? toStringKeyMap(m) : Map.of();
            Object result = executor.apply(args);
            return JsonUtil.toJsonStr(result == null ? "" : result);
        } catch (Exception e) {
            return JsonUtil.toJsonStr(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** 由参数描述生成 JSON Schema：type=object + properties + required。 */
    private static String buildSchema(List<ToolParameter> parameters) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new java.util.ArrayList<>();
        if (parameters != null) {
            for (ToolParameter p : parameters) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", p.jsonType());
                if (p.description() != null && !p.description().isBlank()) {
                    prop.put("description", p.description());
                }
                properties.put(p.name(), prop);
                required.add(p.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return JsonUtil.toJsonStr(schema);
    }

    /** 将 Map 的键统一转为 String，适配 JSON 解析结果。 */
    private static Map<String, Object> toStringKeyMap(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>(m.size());
        for (Map.Entry<?, ?> e : m.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }
}
