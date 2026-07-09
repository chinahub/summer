package cn.jiebaba.summer.ai.chat;

/** 单次对话的可选项，覆盖全局默认配置。 */
public class ChatOptions {

    private final String model;
    private final Double temperature;
    private final Integer maxTokens;

    public ChatOptions(String model, Double temperature, Integer maxTokens) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
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

    /** 链式构造器。 */
    public static class Builder {
        private String model;
        private Double temperature;
        private Integer maxTokens;

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

        public ChatOptions build() {
            return new ChatOptions(model, temperature, maxTokens);
        }
    }
}
