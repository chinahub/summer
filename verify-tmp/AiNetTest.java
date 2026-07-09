import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.model.openai.OpenAiCompatibleChatModel;

import java.sql.*;
import java.time.Duration;
import java.util.stream.Stream;

public class AiNetTest {
    public static void main(String[] args) throws Exception {
        String dbUrl = "jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres";
        String user = "postgres.feodqkgqrxzvcnmuvjgt";
        String pass = "MjpeDL+s=DY7D@|q";
        String name = null, baseUrl = null, apiKey = null;
        try (Connection con = DriverManager.getConnection(dbUrl, user, pass);
             PreparedStatement ps = con.prepareStatement("select name, baseurl, api_key from public.ai_provider_info where name=?")) {
            ps.setString(1, "deepseek");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { name = rs.getString(1); baseUrl = rs.getString(2); apiKey = rs.getString(3); }
            }
        }
        if (apiKey == null) { System.out.println("DB 中无 deepseek 记录"); return; }
        System.out.println("从DB读取: provider=" + name + " baseUrl=" + baseUrl
                + " key=" + apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4));

        OpenAiCompatibleChatModel model = new OpenAiCompatibleChatModel(
                baseUrl, apiKey, "deepseek-chat", Duration.ofSeconds(60), 0.7, 1024);
        ChatClient client = ChatClient.create(model);

        System.out.println("=== 非流式调用 ===");
        ChatResponse resp = client.prompt("你是一个简洁的助手，用中文回答").user("用一句话介绍你自己").call();
        System.out.println("content=" + resp.content());
        System.out.println("reasoning=" + resp.reasoningContent());
        System.out.println("finishReason=" + resp.finishReason());
        if (resp.metadata() != null) {
            System.out.println("usage: prompt=" + resp.metadata().promptTokens()
                    + " completion=" + resp.metadata().completionTokens()
                    + " total=" + resp.metadata().totalTokens()
                    + " cacheHit=" + resp.metadata().promptCacheHitTokens());
        }

        System.out.println("=== 流式调用 ===");
        StringBuilder sb = new StringBuilder();
        try (Stream<ChatResponse> s = client.prompt("你是一个简洁的助手").user("数1到5，只输出数字").stream()) {
            s.forEach(c -> { if (c.content() != null) { System.out.print(c.content()); sb.append(c.content()); } });
        }
        System.out.println();
        System.out.println("streamed=" + sb);
        System.out.println("AI NET TEST OK");
    }
}
