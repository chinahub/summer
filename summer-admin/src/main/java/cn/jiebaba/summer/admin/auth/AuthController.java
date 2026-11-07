package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.admin.auth.captcha.CaptchaResult;
import cn.jiebaba.summer.admin.auth.captcha.CaptchaService;
import cn.jiebaba.summer.admin.auth.dto.LoginRequest;
import cn.jiebaba.summer.admin.auth.dto.LoginResult;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.system.menu.RouterVo;
import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.http.WebRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证接口：验证码、登录、登出、当前用户资料与动态路由。
 * 登录/验证码匿名可访问；登出/userinfo/routers 由安全链要求有效令牌。
 * 登录成功 data 以 snake_case 输出（access_token/token_type/expires_in）对接前端。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    /** 图形验证码：返回开关、UUID 与 Base64 图片。 */
    @GetMapping("/captcha")
    public R<CaptchaResult> captcha() {
        return R.ok(captchaService.issue());
    }

    /** 登录：校验验证码 + 数据库用户认证后签发访问令牌。 */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest request, WebRequest webRequest) {
        LoginResult result = authService.login(request,
                webRequest.remoteAddress(), webRequest.header("User-Agent"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("access_token", result.getAccessToken());
        data.put("token_type", result.getTokenType());
        data.put("expires_in", result.getExpiresIn());
        return R.ok(data);
    }

    /** 登出：记录操作日志；无状态令牌不在服务端撤销，前端负责清除。 */
    @PostMapping("/logout")
    public R<Void> logout(@AuthenticationPrincipal String username, WebRequest webRequest) {
        authService.logout(username, webRequest.remoteAddress(), webRequest.header("User-Agent"));
        return R.ok();
    }

    /** 当前用户资料：基本信息、角色与权限集合（不含密码）。 */
    @GetMapping("/userinfo")
    public R<Map<String, Object>> userinfo(@AuthenticationPrincipal String username) {
        return R.ok(authService.userinfo(username));
    }

    /** 当前用户动态路由。 */
    @GetMapping("/routers")
    public R<List<RouterVo>> routers(@AuthenticationPrincipal String username) {
        return R.ok(authService.routers(username));
    }
}
