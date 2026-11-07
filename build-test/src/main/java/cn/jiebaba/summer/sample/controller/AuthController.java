package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示需要认证的端点与 {@link AuthenticationPrincipal} 注入。
 * {@code /login} 端点由 JWT 登录过滤器处理（无需控制器）；
 * 本控制器暴露 {@code /me} 以查看当前登录主体。
 */
@RestController
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", principal.getUsername());
        body.put("authorities", principal.getAuthorities());
        return body;
    }
}
