package cn.jiebaba.summer.admin.auth.dto;

/** 登录结果：访问令牌、令牌类型与有效期（秒）。Java 侧驼峰命名，控制器边界再转 snake_case。 */
public class LoginResult {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;

    public LoginResult(String accessToken, String tokenType, long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
