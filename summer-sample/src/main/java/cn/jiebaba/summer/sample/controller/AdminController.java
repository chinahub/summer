package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.annotation.PermitAll;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示方法级授权。
 * <p>{@code /admin/info} 同时受 URL 级别（{@code /admin/**} 要求 ADMIN 角色）
 * 与方法级别（{@code @PreAuthorize}）保护；{@code /secret} 在 URL 级别放行，
 * 但 <em>仅</em> 由 {@code @PreAuthorize} 保护，以隔离方法级别的强制校验。
 */
@RestController
public class AdminController {

    @GetMapping("/admin/info")
    @PreAuthorize(roles = {"ADMIN"})
    public Map<String, Object> adminInfo(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("area", "admin");
        body.put("message", "welcome, " + principal.getUsername());
        return body;
    }

    @GetMapping("/secret")
    @PreAuthorize(roles = {"ADMIN"})
    public Map<String, Object> secret(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revealed", true);
        body.put("to", principal.getUsername());
        return body;
    }

    @GetMapping("/public/hello")
    @PermitAll
    public Map<String, String> publicHello() {
        return Map.of("message", "no auth needed");
    }
}
