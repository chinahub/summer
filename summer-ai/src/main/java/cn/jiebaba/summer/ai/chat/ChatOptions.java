package cn.jiebaba.summer.ai.chat;

import java.util.List;

/** 单次对话的可选项，覆盖全局默认配置，并可携带工具定义与工具选择策略。 */
public class ChatOptions {

    private final String model;
    private final Double temperature;
    private final Integer maxTokens;
    private final List<ToolDefinition> tools;
    private final String toolChoice;

    public ChatOptions(String model, Double temperature, Integer maxTokens) {
        this(model, temperature, maxTokens, List.of(), null);
    }

    public ChatOptions(String model, Double temperature, Integer maxTokens,
                       List<ToolDefinition> tools, String toolChoice) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.toolChoice = toolChoice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    /** 链式构造器。 */
    public static class Builder {
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private List<ToolDefinition> tools = List.of();
        private String toolChoice;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public ChatOptions build() {
            return new ChatOptions(model, temperature, maxTokens, tools, toolChoice);
        }
    }
}
