package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.model.Provider;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;
import cn.jiebaba.summer.ai.tools.Tool;
import cn.jiebaba.summer.ai.tools.ToolCallingChatModel;
import cn.jiebaba.summer.ai.tools.ToolParameter;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.postgresql.ds.PGSimpleDataSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式工具调用循环的真实 LLM 冒烟测试。
 * <p>LLM 凭据实时读取自 PostgreSQL 的 public.ai_provider_info 表（name/baseurl/api_key 列），
 * 复用 summer-sample 的 summer.datasource.* 配置连接数据库，经 summer-data 的 SqlExecutor + RowMapper 查询。
 * 表无 model 列时按 name 解析厂商默认模型（如 deepseek -> deepseek-chat）。
 * <p>用加法工具验证端到端：流式累积 tool_calls 增量 -> 执行工具 -> 续接流式给出最终答案。
 * 当数据源未配置、DB 不可达、表无可用记录或厂商未识别时通过 {@link Assumptions#assumeTrue} 跳过。
 * 需支持 Function Calling 的模型（DeepSeek/GLM/MiniMax 等）。
 */
public class StreamingToolLoopLlmTest {

    /** ai_provider_info 表读取到的厂商凭据。 */
    private record ProviderInfo(String name, String baseUrl, String apiKey) {
    }

    /** 端到端：实时读取 DB 凭据 -> 真实 LLM 流式调用 add 工具 -> 返回计算结果。 */
    @Test
    public void streamingToolLoopWithRealLlm() {
        Environment env = new Environment();
        String url = env.getProperty("summer.datasource.url");
        String user = env.getProperty("summer.datasource.username");
        String pass = env.getProperty("summer.datasource.password");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "无 summer.datasource.url 配置，跳过真实 LLM 流式工具冒烟测试");

        // 实时从 ai_provider_info 表读取厂商凭据
        ProviderInfo info = null;
        try {
            info = loadProviderInfo(url, user, pass);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "读取 ai_provider_info 失败，跳过: " + e.getMessage());
        }
        Assumptions.assumeTrue(info != null && info.apiKey() != null && !info.apiKey().isBlank(),
                "ai_provider_info 无可用记录或 api_key，跳过真实 LLM 流式工具冒烟测试");

        Provider provider = Provider.from(info.name());
        Assumptions.assumeTrue(provider != null,
                "ai_provider_info.name 未识别为已知厂商: " + info.name() + "，跳过");
        String model = provider.getDefaultModel();
        String baseUrl = info.baseUrl() != null && !info.baseUrl().isBlank()
                ? info.baseUrl() : provider.getDefaultBaseUrl();
        System.out.println("[stream] provider=" + info.name() + ", baseurl=" + baseUrl + ", model=" + model);

        AtomicInteger executed = new AtomicInteger();
        Tool addTool = new Tool("add", "两整数相加并返回和",
                List.of(ToolParameter.integer("a", "加数"), ToolParameter.integer("b", "被加数")),
                args -> {
                    int a = ((Number) args.get("a")).intValue();
                    int b = ((Number) args.get("b")).intValue();
                    executed.incrementAndGet();
                    System.out.println("[tool] add(" + a + "," + b + ") -> " + (a + b));
                    return Map.of("sum", a + b);
                });
        ChatModel base = new OpenAiCompatibleChatModel(
                baseUrl, info.apiKey(), model, Duration.ofSeconds(60), 0.0, 2048);
        ChatModel chatModel = new ToolCallingChatModel(base, List.of(addTool));

        StringBuilder text = new StringBuilder();
        System.out.println("[stream] tokens:");
        try (var stream = ChatClient.create(chatModel).prompt()
                .system("你是计算助手，遇到加法必须调用 add 工具，不得自行心算。")
                .user("请用 add 工具计算 123 + 456，然后直接告诉我结果数字。")
                .stream()) {
            stream.forEach(c -> {
                if (c.content() != null) {
                    System.out.print(c.content());
                    text.append(c.content());
                }
            });
        }
        System.out.println();
        System.out.println("[stream] done, tool executed=" + executed.get() + ", content=" + text);

        Assertions.assertTrue(executed.get() >= 1,
                "流式工具循环应触发 add 工具执行（需支持 Function Calling 的模型）");
        Assertions.assertFalse(text.toString().isBlank(), "流式应输出非空内容");
        Assertions.assertTrue(text.toString().contains("579"),
                "最终回复应包含工具计算结果 579，实际: " + text);
    }

    /** 经 SqlExecutor + RowMapper 查询 ai_provider_info 首条记录；无记录返回 null。 */
    private static ProviderInfo loadProviderInfo(String url, String user, String pass) throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(user);
        ds.setPassword(pass);
        ds.setLoginTimeout(10);
        SqlExecutor sqlExecutor = new SqlExecutor(ds);
        List<ProviderInfo> rows = sqlExecutor.query(
                new SqlBuilder.Sql(
                        "SELECT name, baseurl, api_key FROM ai_provider_info ORDER BY id LIMIT 1",
                        List.of()),
                (rs, n) -> new ProviderInfo(rs.getString("name"), rs.getString("baseurl"), rs.getString("api_key")));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
