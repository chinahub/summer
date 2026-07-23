package cn.jiebaba.summer.ai.chat;

import cn.jiebaba.summer.ai.advisor.AdvisedRequest;
import cn.jiebaba.summer.ai.advisor.AdvisedResponse;
import cn.jiebaba.summer.ai.advisor.Advisor;
import cn.jiebaba.summer.ai.advisor.AdvisorChains;
import cn.jiebaba.summer.ai.structured.BeanOutputConverter;
import cn.jiebaba.summer.ai.structured.StructuredResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/**
 * ChatModel 的 fluent 门面：链式拼装消息与选项后发起同步或流式调用。
 * 用法：ChatClient.create(model).prompt().system("你是助手").user("你好").call()
 */
public class ChatClient {

    private final ChatModel chatModel;

    private ChatClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public static ChatClient create(ChatModel chatModel) {
        return new ChatClient(chatModel);
    }

    public PromptBuilder prompt() {
        return new PromptBuilder(chatModel);
    }

    public PromptBuilder prompt(String systemText) {
        return new PromptBuilder(chatModel).system(systemText);
    }

    /** 链式请求构造器，累积消息、选项与顾问。 */
    public static class PromptBuilder {
        private final ChatModel chatModel;
        private final List<Message> messages = new ArrayList<>();
        private ChatOptions options;
        private final List<Advisor> advisors = new ArrayList<>();

        PromptBuilder(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        public PromptBuilder system(String content) {
            messages.add(new SystemMessage(content));
            return this;
        }

        public PromptBuilder user(String content) {
            messages.add(new UserMessage(content));
            return this;
        }

        public PromptBuilder assistant(String content) {
            messages.add(new AssistantMessage(content));
            return this;
        }

        /** 直接追加消息列表，便于回放历史或注入多模态/工具消息。 */
        public PromptBuilder messages(List<Message> messages) {
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return this;
        }

        public PromptBuilder options(ChatOptions options) {
            this.options = options;
            return this;
        }

        /** 追加顾问到链尾（忽略 null）。 */
        public PromptBuilder advisors(Advisor... advisors) {
            if (advisors != null) {
                for (Advisor advisor : advisors) {
                    if (advisor != null) {
                        this.advisors.add(advisor);
                    }
                }
            }
            return this;
        }

        /** 追加顾问列表到链尾（忽略 null）。 */
        public PromptBuilder advisors(List<Advisor> advisors) {
            if (advisors != null) {
                for (Advisor advisor : advisors) {
                    if (advisor != null) {
                        this.advisors.add(advisor);
                    }
                }
            }
            return this;
        }

        public ChatResponse call() {
            return executeCall(messages, options).response();
        }

        public Stream<ChatResponse> stream() {
            return executeStream(messages, options);
        }

        /**
         * 结构化输出：将模型回复直接转换为指定类型的实例。
         * 内部追加 JSON Schema 格式提示并强制 JSON 响应格式，再解析返回内容。
         *
         * @param type 目标 Java 类型（record 或 bean）
         * @param <T>  目标类型
         * @return 解析后的目标实例
         */
        public <T> T entity(Class<T> type) {
            return entityResponse(type).entity();
        }

        /**
         * 结构化输出：返回目标实例与原始响应的组合，便于同时访问元数据。
         *
         * @param type 目标 Java 类型（record 或 bean）
         * @param <T>  目标类型
         * @return 包含实体与原始响应的结构化响应
         */
        public <T> StructuredResponse<T> entityResponse(Class<T> type) {
            BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
            List<Message> finalMessages = mergeFormat(messages, converter.getFormat());
            ChatOptions finalOptions = ensureJsonOptions(options);
            AdvisedResponse advised = executeCall(finalMessages, finalOptions);
            T entity = converter.convert(advised.response().content());
            return new StructuredResponse<>(entity, advised.response());
        }

        /**
         * 执行同步调用：无顾问时直调模型，有顾问时经顾问链推进后调用模型。
         *
         * @param msgs    消息列表
         * @param options 调用选项
         * @return 顾问链处理后的响应
         */
        private AdvisedResponse executeCall(List<Message> msgs, ChatOptions options) {
            if (advisors.isEmpty()) {
                ChatResponse response = chatModel.call(new Prompt(msgs, options));
                return new AdvisedResponse(response, new LinkedHashMap<>());
            }
            return AdvisorChains.call(chatModel, advisors, AdvisedRequest.of(msgs, options));
        }

        /**
         * 执行流式调用：无顾问时直调模型流，有顾问时经顾问链推进后调用模型流并解包响应。
         *
         * @param msgs    消息列表
         * @param options 调用选项
         * @return 响应片段流
         */
        private Stream<ChatResponse> executeStream(List<Message> msgs, ChatOptions options) {
            if (advisors.isEmpty()) {
                return chatModel.stream(new Prompt(msgs, options));
            }
            return AdvisorChains.stream(chatModel, advisors, AdvisedRequest.of(msgs, options))
                    .map(AdvisedResponse::response);
        }

        /**
         * 将格式提示合并进首条 system 消息；若无 system 消息则在首位插入。
         *
         * @param source 原始消息列表
         * @param format 格式提示文本
         * @return 合并后的新消息列表
         */
        private List<Message> mergeFormat(List<Message> source, String format) {
            List<Message> result = new ArrayList<>(source.size() + 1);
            boolean merged = false;
            for (Message message : source) {
                if (!merged && "system".equals(message.role())) {
                    result.add(new SystemMessage(message.content() + "\n\n" + format));
                    merged = true;
                } else {
                    result.add(message);
                }
            }
            if (!merged) {
                result.add(0, new SystemMessage(format));
            }
            return result;
        }

        /**
         * 确保响应格式为 JSON：复用已有选项并补齐 responseFormat，缺失时新建默认选项。
         *
         * @param options 原始选项，可为 null
         * @return 强制 JSON 响应格式的选项
         */
        private ChatOptions ensureJsonOptions(ChatOptions options) {
            if (options != null && "json_object".equals(options.getResponseFormat())) {
                return options;
            }
            ChatOptions.Builder builder = ChatOptions.builder();
            if (options != null) {
                builder.model(options.getModel())
                        .temperature(options.getTemperature())
                        .maxTokens(options.getMaxTokens())
                        .tools(options.getTools())
                        .toolChoice(options.getToolChoice());
            }
            return builder.responseFormat("json_object").build();
        }
    }
}
