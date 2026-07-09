package cn.jiebaba.summer.ai.model.openai;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatOptions;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.ChatResponseMetadata;
import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
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

    /** 组装请求体 JSON：model、messages、stream、temperature、max_tokens。 */
    private String buildRequestBody(Prompt prompt, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(prompt));
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message message : prompt.getMessages()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.role());
            item.put("content", message.content());
            messages.add(item);
        }
        body.put("messages", messages);
        body.put("stream", stream);
        ChatOptions options = prompt.getOptions();
        Double temp = options != null && options.getTemperature() != null ? options.getTemperature() : temperature;
        if (temp != null) {
            body.put("temperature", temp);
        }
        Integer maxTok = options != null && options.getMaxTokens() != null ? options.getMaxTokens() : maxTokens;
        if (maxTok != null) {
            body.put("max_tokens", maxTok);
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

    /** 解析非流式完整响应：choices[0].message 的 content/reasoning_content 与 usage 用量。 */
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
        ChatResponseMetadata metadata = parseUsage(root.getStr("model"), root.getJSONObject("usage"));
        return new ChatResponse(content, reasoning, finishReason, metadata);
    }

    /** 解析流式 SSE 单帧：choices[0].delta 的增量 content/reasoning_content；解析失败返回 null 跳过。 */
    private ChatResponse parseChunk(String json) {
        try {
            JSONObject root = JsonUtil.parseObj(json);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            String content = delta != null ? delta.getStr("content") : null;
            String reasoning = delta != null ? delta.getStr("reasoning_content") : null;
            String finishReason = choice.getStr("finish_reason");
            return new ChatResponse(content, reasoning, finishReason, null);
        } catch (Exception e) {
            return null;
        }
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
