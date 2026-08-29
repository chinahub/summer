package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.web.JwtLoginFilter;
import cn.jiebaba.summer.security.web.JwtRefreshFilter;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 验证登录/刷新端点对异常请求体的容错：带 UTF-8 BOM 的合法 JSON 可正常登录；
 * 非法 JSON（纯文本）返回 400 而非 500，且错误消息包含实际非法字符；
 * 空请求体维持既有 400 缺字段行为不变。
 */
public class LoginBodyParseTest {

    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef-test-secret".getBytes(StandardCharsets.UTF_8);

    private final JwtEncoder encoder = new JwtEncoder(SECRET);
    private final JwtDecoder decoder = new JwtDecoder(SECRET);

    /** 认证 stub：任意凭据直接返回已认证令牌（本测试只关心请求体解析，不关心认证结果）。 */
    private final AuthenticationManager manager = auth -> new UsernamePasswordAuthenticationToken(
            auth.getName(), null, List.of(SimpleGrantedAuthority.roleOf("USER")));

    private final JwtLoginFilter loginFilter = new JwtLoginFilter(manager, encoder, 3600, 7200, "/login");
    private final JwtRefreshFilter refreshFilter =
            new JwtRefreshFilter(decoder, encoder, 3600, 7200, "/refresh", false);

    record Resp(String status, String body) {}

    /** 带请求体构造 POST WebRequest（Content-Length 按字节数计算，支持 BOM 等多字节内容）。 */
    private WebRequest post(String path, byte[] body) throws Exception {
        String head = "POST " + path + " HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\nContent-Length: "
                + body.length + "\r\n\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[headBytes.length + body.length];
        System.arraycopy(headBytes, 0, all, 0, headBytes.length);
        System.arraycopy(body, 0, all, headBytes.length, body.length);
        return new WebRequest(RawHttpRequest.parse(new ByteArrayInputStream(all), 8192, 8388608));
    }

    /** 执行过滤器并提交响应到内存流，解析出状态码与响应体文本。 */
    private Resp respond(WebRequest req, Filter filter) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WebResponse res = new WebResponse(Channels.newChannel(out));
        filter.doFilter(req, res, new FilterChain(List.of(), (r, s) -> { }));
        res.commit();
        String raw = out.toString(StandardCharsets.UTF_8);
        String statusLine = raw.substring(0, raw.indexOf("\r\n"));
        String body = raw.substring(raw.indexOf("\r\n\r\n") + 4);
        return new Resp(statusLine.split(" ")[1], body);
    }

    /** 在 JSON 文本前拼接 UTF-8 BOM（EF BB BF）。 */
    private static byte[] withBom(String json) {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + payload.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(payload, 0, all, bom.length, payload.length);
        return all;
    }

    @Test
    void loginWithBomBodySucceeds() throws Exception {
        Resp resp = respond(post("/login", withBom("{\"username\":\"admin\",\"password\":\"admin123\"}")), loginFilter);
        Assertions.assertEquals("200", resp.status(), "BOM 请求体应正常登录: " + resp.body());
        Assertions.assertTrue(resp.body().contains("\"accessToken\""), "应返回访问令牌");
    }

    @Test
    void loginWithPlainTextBodyReturns400() throws Exception {
        Resp resp = respond(post("/login", "abc".getBytes(StandardCharsets.UTF_8)), loginFilter);
        Assertions.assertEquals("400", resp.status(), "非法 JSON 应返回 400 而非 500: " + resp.body());
        Assertions.assertTrue(resp.body().contains("invalid JSON body"), "错误消息应说明请求体非法: " + resp.body());
        Assertions.assertTrue(resp.body().contains("'a'"), "错误消息应包含实际非法字符: " + resp.body());
    }

    @Test
    void loginWithEmptyBodyStill400RequiredFields() throws Exception {
        Resp resp = respond(post("/login", new byte[0]), loginFilter);
        Assertions.assertEquals("400", resp.status(), "空请求体维持 400: " + resp.body());
        Assertions.assertTrue(resp.body().contains("username and password are required"),
                "错误消息应为缺字段语义: " + resp.body());
    }

    @Test
    void refreshWithPlainTextBodyReturns400() throws Exception {
        Resp resp = respond(post("/refresh", "abc".getBytes(StandardCharsets.UTF_8)), refreshFilter);
        Assertions.assertEquals("400", resp.status(), "非法 JSON 应返回 400 而非 500: " + resp.body());
    }
}
