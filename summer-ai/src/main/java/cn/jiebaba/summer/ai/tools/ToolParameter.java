package cn.jiebaba.summer.ai.tools;

/**
 * 工具参数描述：参数名、说明与 JSON Schema 类型。
 * 由静态工厂方法构造常用类型，避免调用方手写类型字符串。
 */
public record ToolParameter(String name, String description, String jsonType) {

    public static ToolParameter string(String name, String description) {
        return new ToolParameter(name, description, "string");
    }

    public static ToolParameter integer(String name, String description) {
        return new ToolParameter(name, description, "integer");
    }

    public static ToolParameter number(String name, String description) {
        return new ToolParameter(name, description, "number");
    }

    public static ToolParameter bool(String name, String description) {
        return new ToolParameter(name, description, "boolean");
    }

    public static ToolParameter of(String name, String description, String jsonType) {
        return new ToolParameter(name, description, jsonType);
    }
}
