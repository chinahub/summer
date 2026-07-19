package cn.jiebaba.summer.ai.model.openai;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatOptions;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.ChatResponseMetadata;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.chat.ToolCall;
import cn.jiebaba.summer.ai.chat.ToolDefinition;
import cn.jiebaba.summer.ai.chat.ToolMessage;
import cn.jiebaba.summer.ai.chat.content.ContentPart;
import cn.jiebaba.summer.ai.chat.content.ImageUrlPart;
import cn.jiebaba.summer.ai.chat.content.InputAudioPart;
import cn.jiebaba.summer.ai.chat.content.TextPart;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONArray;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议的 ChatModel 实现，纯 JDK HttpURLConnection（阻塞式，无 selector），
 * 零第三方依赖，与 summer-web 同样规避受限沙箱的 loopback 管道问题。
 * 覆盖 DeepSeek、GLM（智谱 OpenAI 兼容端点）、MiniMax 等国内厂商：
 * 仅 base-url 与模型名不同，请求、响应与 SSE 流式协议一致。
 * 支持多模态内容（图片/语音）、Function Calling 工具调用与思维链解析。
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final Double temperature;
    private final Integer maxTokens;

    public OpenAiCompatibleChatModel(String baseUrl, String apiKey, String model,
                                     Duration timeout, Double temperature, Integer maxTokens) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(buildRequestBody(prompt, false), false);
            int code = conn.getResponseCode();
            String body = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new AiException("大模型调用失败 HTTP " + code + ": " + body);
            }
            return parseResponse(body);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("大模型调用异常: " + e.getMessage(), e);
        } finally {
            disconnect(conn);
        }
    }

    /** 流式调用：发送 stream=true 请求，逐行解析 SSE data 帧为增量响应片段；调用方应关闭返回的 Stream 以释放连接。 */
    @Override
    public Stream<ChatResponse> stream(Prompt prompt) {
        try {
            HttpURLConnection conn = openConnection(buildRequestBody(prompt, true), true);
            int code = conn.getResponseCode();
            if (code >= 400) {
                String err = readAll(conn.getErrorStream());
                disconnect(conn);
                throw new AiException("大模型流式调用失败 HTTP " + code + ": " + err);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            final HttpURLConnection toClose = conn;
            return reader.lines()
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim())
                    .takeWhile(data -> !"[DONE]".equals(data))
                    .filter(data -> !data.isEmpty())
                    .map(this::parseChunk)
                    .filter(Objects::nonNull)
                    .onClose(() -> {
                        try {
                            reader.close();
                        } catch (Exception ignored) {
                            // 忽略关闭异常
                        }
                        disconnect(toClose);
                    });
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("大模型流式调用异常: " + e.getMessage(), e);
        }
    }

    /** 建立到 chat/completions 的 POST 连接并写出请求体，返回未读取响应的连接。 */
    private HttpURLConnection openConnection(String body, boolean stream) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/chat/completions").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout((int) timeout.toMillis());
        conn.setReadTimeout((int) timeout.toMillis());
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    /** 组装请求体 JSON：model、messages（含多模态/工具消息）、stream、temperature、max_tokens、tools、tool_choice。 */
    private String buildRequestBody(Prompt prompt, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(prompt));
        body.put("messages", serializeMessages(prompt.getMessages()));
        body.put("stream", stream);
        if (stream) {
            Map<String, Object> streamOptions = new LinkedHashMap<>();
            streamOptions.put("include_usage", true);
            body.put("stream_options", streamOptions);
        }
        ChatOptions options = prompt.getOptions();
        Double temp = options != null && options.getTemperature() != null ? options.getTemperature() : temperature;
        if (temp != null) {
            body.put("temperature", temp);
        }
        Integer maxTok = options != null && options.getMaxTokens() != null ? options.getMaxTokens() : maxTokens;
        if (maxTok != null) {
            body.put("max_tokens", maxTok);
        }
        if (options != null) {
            putTools(body, options);
            putResponseFormat(body, options);
        }
        return JsonUtil.toJsonStr(body);
    }

    private String resolveModel(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options != null && options.getModel() != null) {
            return options.getModel();
        }
        return model;
    }

    /** 将消息列表序列化为 OpenAI messages 数组：tool 消息带 tool_call_id，assistant 可带 tool_calls，user 可为多模态数组。 */
    private List<Map<String, Object>> serializeMessages(List<Message> messages) {
        List<Map<String, Object>> list = new ArrayList<>(messages.size());
        for (Message message : messages) {
            list.add(serializeMessage(message));
        }
        return list;
    }

    /** 按角色序列化单条消息：tool/assistant(带工具调用)/user(多模态) 走专用分支，其余回退为纯文本。 */
    private Map<String, Object> serializeMessage(Message message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", message.role());
        if ("tool".equals(message.role())) {
            if (message instanceof ToolMessage tm) {
                item.put("tool_call_id", tm.toolCallId());
            }
            item.put("content", message.content());
            return item;
        }
        if ("assistant".equals(message.role()) && !message.toolCalls().isEmpty()) {
            item.put("content", message.content());
            item.put("tool_calls", serializeToolCalls(message.toolCalls()));
            return item;
        }
        if ("user".equals(message.role()) && !message.parts().isEmpty()) {
            List<ContentPart> parts = message.parts();
            if (parts.size() == 1 && parts.get(0) instanceof TextPart tp) {
                item.put("content", tp.text());
            } else {
                item.put("content", serializeParts(parts));
            }
            return item;
        }
        item.put("content", message.content());
        return item;
    }

    /** 序列化 assistant 消息中的工具调用列表为 OpenAI tool_calls 数组结构。 */
    private List<Map<String, Object>> serializeToolCalls(List<ToolCall> toolCalls) {
        List<Map<String, Object>> calls = new ArrayList<>(toolCalls.size());
        for (ToolCall tc : toolCalls) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", tc.id());
            call.put("type", "function");
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tc.name());
            fn.put("arguments", tc.arguments() == null ? "" : tc.arguments());
            call.put("function", fn);
            calls.add(call);
        }
        return calls;
    }

    /** 将多模态内容片段列表序列化为 OpenAI content 数组（type + 对应载荷）。 */
    private List<Map<String, Object>> serializeParts(List<ContentPart> parts) {
        List<Map<String, Object>> list = new ArrayList<>(parts.size());
        for (ContentPart part : parts) {
            list.add(serializePart(part));
        }
        return list;
    }

    /** 按 sealed 类型分派序列化单个内容片段：文本/图片/语音。 */
    private Map<String, Object> serializePart(ContentPart part) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", part.type());
        switch (part) {
            case TextPart t -> m.put("text", t.text());
            case ImageUrlPart img -> {
                Map<String, Object> url = new LinkedHashMap<>();
                url.put("url", img.url());
                if (img.detail() != null) {
                    url.put("detail", img.detail());
                }
                m.put("image_url", url);
            }
            case InputAudioPart aud -> {
                Map<String, Object> audio = new LinkedHashMap<>();
                audio.put("data", aud.data());
                audio.put("format", aud.format());
                m.put("input_audio", audio);
            }
        }
        return m;
    }

    /** 当 options 携带工具定义时注入 tools 字段与可选 tool_choice 策略。 */
    private void putTools(Map<String, Object> body, ChatOptions options) {
        List<ToolDefinition> tools = options.getTools();
        if (tools == null || tools.isEmpty()) {
            return;
        }
        List<Map<String, Object>> arr = new ArrayList<>(tools.size());
        for (ToolDefinition td : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", td.name());
            if (td.description() != null) {
                fn.put("description", td.description());
            }
            Object params = td.parametersJson() == null || td.parametersJson().isBlank()
                    ? Map.of("type", "object", "properties", Map.of())
                    : JsonUtil.parse(td.parametersJson());
            fn.put("parameters", params);
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", fn);
            arr.add(tool);
        }
        body.put("tools", arr);
        if (options.getToolChoice() != null) {
            body.put("tool_choice", options.getToolChoice());
        }
    }

    /** 当 options 指定响应格式时注入 response_format 字段（如 json_object 启用 JSON 模式）。 */
    private void putResponseFormat(Map<String, Object> body, ChatOptions options) {
        String format = options.getResponseFormat();
        if (format == null || format.isBlank()) {
            return;
        }
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", format);
        body.put("response_format", responseFormat);
    }

    /** 将响应输入流完整读取为 UTF-8 字符串；流为空返回空串。 */
    private String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = is.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    /** 解析非流式完整响应：choices[0].message 的 content/reasoning_content/tool_calls 与 usage 用量。 */
    private ChatResponse parseResponse(String json) {
        JSONObject root = JsonUtil.parseObj(json);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return new ChatResponse(null, null, null, null);
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        String content = message != null ? message.getStr("content") : null;
        String reasoning = message != null ? message.getStr("reasoning_content") : null;
        String finishReason = choice.getStr("finish_reason");
        JSONArray rawCalls = message != null ? message.getJSONArray("tool_calls") : null;
        List<ToolCall> toolCalls = parseToolCalls(rawCalls);
        ChatResponseMetadata metadata = parseUsage(root.getStr("model"), root.getJSONObject("usage"));
        return new ChatResponse(content, reasoning, finishReason, toolCalls, metadata);
    }

    /** 解析流式 SSE 单帧：choices[0].delta 的增量 content/reasoning_content/tool_calls；解析失败返回 null 跳过。 */
    private ChatResponse parseChunk(String json) {
        try {
            JSONObject root = JsonUtil.parseObj(json);
            ChatResponseMetadata metadata = parseUsage(root.getStr("model"), root.getJSONObject("usage"));
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                // stream_options.include_usage=true 时末帧 choices 为空、仅含 usage，外抛以补全 token 观测
                return metadata == null ? null : new ChatResponse(null, null, null, List.of(), metadata);
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            String content = delta != null ? delta.getStr("content") : null;
            String reasoning = delta != null ? delta.getStr("reasoning_content") : null;
            String finishReason = choice.getStr("finish_reason");
            JSONArray rawCalls = delta != null ? delta.getJSONArray("tool_calls") : null;
            List<ToolCall> toolCalls = parseToolCalls(rawCalls);
            return new ChatResponse(content, reasoning, finishReason, toolCalls, metadata);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 message/delta 中的 tool_calls 数组为 ToolCall 列表；流式增量携带 index 供上游累积，非流式无 index 记为 -1。 */
    private List<ToolCall> parseToolCalls(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<ToolCall> list = new ArrayList<>(arr.size());
        for (Object o : arr) {
            if (!(o instanceof JSONObject tc)) {
                continue;
            }
            String id = tc.getStr("id");
            JSONObject fn = tc.getJSONObject("function");
            String name = fn != null ? fn.getStr("name") : null;
            String args = fn != null ? fn.getStr("arguments") : null;
            Integer idx = tc.getInt("index");
            list.add(new ToolCall(id, name, args, idx != null ? idx : -1));
        }
        return list;
    }

    private ChatResponseMetadata parseUsage(String model, JSONObject usage) {
        if (usage == null) {
            return null;
        }
        return new ChatResponseMetadata(
                model,
                usage.getLong("prompt_tokens"),
                usage.getLong("completion_tokens"),
                usage.getLong("total_tokens"),
                usage.getLong("prompt_cache_hit_tokens"));
    }

    private void disconnect(HttpURLConnection conn) {
        if (conn != null) {
            conn.disconnect();
        }
    }
}
