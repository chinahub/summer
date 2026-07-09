package cn.jiebaba.summer.ai.embedding.openai;

import cn.jiebaba.summer.ai.AiException;
import cn.jiebaba.summer.ai.embedding.Embedding;
import cn.jiebaba.summer.ai.embedding.EmbeddingModel;
import cn.jiebaba.summer.ai.embedding.EmbeddingResponse;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONArray;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的 EmbeddingModel 实现，纯 JDK HttpURLConnection，零第三方依赖。
 * 覆盖 DeepSeek、GLM、MiniMax 等厂商的 /embeddings 端点：
 * 请求体 {"model":..., "input":[...]}，响应 data[].embedding 为浮点数组。
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private volatile int dimensions = -1;

    public OpenAiCompatibleEmbeddingModel(String baseUrl, String apiKey, String model, Duration timeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    /** 批量向量化：POST /embeddings，解析 data[].embedding 转为 float[]。 */
    @Override
    public EmbeddingResponse embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new EmbeddingResponse(List.of(), model);
        }
        HttpURLConnection conn = null;
        try {
            conn = openConnection(buildRequestBody(inputs));
            int code = conn.getResponseCode();
            String body = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new AiException("向量化调用失败 HTTP " + code + ": " + body);
            }
            return parseResponse(body);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("向量化调用异常: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 向量维度：首次调用惰性探测一个短文本并缓存。 */
    @Override
    public int dimensions() {
        if (dimensions > 0) {
            return dimensions;
        }
        EmbeddingResponse probe = embed("维度探测");
        if (probe.embeddings().isEmpty()) {
            return 0;
        }
        int dim = probe.embeddings().get(0).dimensions();
        dimensions = dim;
        return dim;
    }

    /** 建立到 /embeddings 的 POST 连接并写出请求体，返回未读取响应的连接。 */
    private HttpURLConnection openConnection(String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/embeddings").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout((int) timeout.toMillis());
        conn.setReadTimeout((int) timeout.toMillis());
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    /** 组装请求体 JSON：model 与 input 数组。 */
    private String buildRequestBody(List<String> inputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", inputs);
        return JsonUtil.toJsonStr(body);
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

    /** 解析响应：data[].index 与 data[].embedding（浮点数组）转 Embedding 列表。 */
    private EmbeddingResponse parseResponse(String json) {
        JSONObject root = JsonUtil.parseObj(json);
        JSONArray data = root.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return new EmbeddingResponse(List.of(), root.getStr("model"));
        }
        List<Embedding> embeddings = new ArrayList<>(data.size());
        for (Object o : data) {
            if (!(o instanceof JSONObject item)) {
                continue;
            }
            int index = item.getInt("index") != null ? item.getInt("index") : embeddings.size();
            embeddings.add(new Embedding(index, toFloatArray(item.getJSONArray("embedding"))));
        }
        return new EmbeddingResponse(embeddings, root.getStr("model"));
    }

    /** 将 JSON 数组转为 float[]，兼容 Number 元素与字符串数字。 */
    private float[] toFloatArray(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return new float[0];
        }
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            Object v = arr.get(i);
            if (v instanceof Number n) {
                vec[i] = n.floatValue();
            } else if (v != null) {
                vec[i] = Float.parseFloat(v.toString());
            }
        }
        return vec;
    }
}
