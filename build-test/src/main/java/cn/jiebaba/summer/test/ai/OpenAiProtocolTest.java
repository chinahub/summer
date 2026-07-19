package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatOptions;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.chat.UserMessage;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

/**
 * OpenAiCompatibleChatModel 协议层测试：经反射直调私有 buildRequestBody/parseChunk，
 * 验证 JSON 模式（response_format 注入）与流式 token 用量（stream_options.include_usage + 末帧 usage 解析）。
 * 不发起网络调用，规避受限沙箱对回环连接的限制，确定性覆盖请求体构建与 SSE 帧解析。
 */
public class OpenAiProtocolTest {

    private final OpenAiCompatibleChatModel model = new OpenAiCompatibleChatModel(
            "http://localhost", "sk-test", "test-model", Duration.ofSeconds(5), 0.7, 1024);

    /** 反射调用私有 buildRequestBody(Prompt, boolean)，返回序列化后的请求体 JSON。 */
    private String buildRequestBody(Prompt prompt, boolean stream) throws Exception {
        Method m = OpenAiCompatibleChatModel.class.getDeclaredMethod(
                "buildRequestBody", Prompt.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(model, prompt, stream);
    }

    /** 反射调用私有 parseChunk(String)，返回单帧解析结果。 */
    private ChatResponse parseChunk(String json) throws Exception {
        Method m = OpenAiCompatibleChatModel.class.getDeclaredMethod("parseChunk", String.class);
        m.setAccessible(true);
        return (ChatResponse) m.invoke(model, json);
    }

    private Prompt prompt(ChatOptions opts) {
        return new Prompt(List.of(new UserMessage("hi")), opts);
    }

    /** JSON 模式：设置 responseFormat=json_object 时，请求体应携带 response_format={"type":"json_object"}。 */
    @Test
    public void jsonModeSendsResponseFormat() throws Exception {
        ChatOptions opts = ChatOptions.builder().responseFormat("json_object").build();
        String body = buildRequestBody(prompt(opts), false);
        JSONObject root = JsonUtil.parseObj(body);
        JSONObject rf = root.getJSONObject("response_format");
        Assert.assertNotNull(rf, "请求体应含 response_format");
        Assert.assertEquals("json_object", rf.getStr("type"));
    }

    /** 默认未设置 responseFormat 时，请求体不应含 response_format 字段。 */
    @Test
    public void noResponseFormatByDefault() throws Exception {
        String body = buildRequestBody(prompt(null), false);
        JSONObject root = JsonUtil.parseObj(body);
        Assert.assertNull(root.getJSONObject("response_format"), "默认不应含 response_format");
    }

    /** 流式请求体应带 stream_options.include_usage=true，非流式不应带 stream_options。 */
    @Test
    public void streamOptionsIncludeUsage() throws Exception {
        JSONObject streamRoot = JsonUtil.parseObj(buildRequestBody(prompt(null), true));
        Assert.assertTrue(streamRoot.getBool("stream"), "流式 stream 应为 true");
        JSONObject so = streamRoot.getJSONObject("stream_options");
        Assert.assertNotNull(so, "流式请求体应含 stream_options");
        Assert.assertTrue(Boolean.TRUE.equals(so.getBool("include_usage")), "include_usage 应为 true");

        JSONObject nonStreamRoot = JsonUtil.parseObj(buildRequestBody(prompt(null), false));
        Assert.assertFalse(nonStreamRoot.getBool("stream"), "非流式 stream 应为 false");
        Assert.assertNull(nonStreamRoot.getJSONObject("stream_options"), "非流式不应含 stream_options");
    }

    /** 流式末帧 choices 为空、仅含 usage 时，parseChunk 应返回携带 metadata 的响应（不再被丢弃）。 */
    @Test
    public void parseChunkUsageOnlyFrame() throws Exception {
        String frame = "{\"model\":\"test-model\",\"choices\":[],"
                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5,"
                + "\"prompt_cache_hit_tokens\":1}}";
        ChatResponse resp = parseChunk(frame);
        Assert.assertNotNull(resp, "末帧 usage 不应被丢弃");
        Assert.assertNotNull(resp.metadata(), "末帧应含 usage 元数据");
        Assert.assertEquals(5L, resp.metadata().totalTokens(), "total_tokens 应为 5");
        Assert.assertEquals(3L, resp.metadata().promptTokens(), "prompt_tokens 应为 3");
        Assert.assertEquals(1L, resp.metadata().promptCacheHitTokens(), "缓存命中应为 1");
        Assert.assertNull(resp.content(), "末帧无内容");
    }

    /** 内容帧不含 usage 时 metadata 为 null；含 usage 时应一并解析填入 metadata。 */
    @Test
    public void parseChunkContentFrameMetadata() throws Exception {
        String noUsage = "{\"model\":\"test-model\",\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}";
        ChatResponse r1 = parseChunk(noUsage);
        Assert.assertEquals("Hello", r1.content());
        Assert.assertNull(r1.metadata(), "无 usage 帧的 metadata 应为 null");

        String withUsage = "{\"model\":\"test-model\",\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
        ChatResponse r2 = parseChunk(withUsage);
        Assert.assertEquals("Hi", r2.content());
        Assert.assertNotNull(r2.metadata(), "含 usage 帧应解析出 metadata");
        Assert.assertEquals(2L, r2.metadata().totalTokens(), "total_tokens 应为 2");
    }
}
