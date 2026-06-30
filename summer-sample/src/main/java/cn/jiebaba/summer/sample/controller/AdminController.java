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
 * Demonstrates method-level authorization.
 * <p>{@code /admin/info} is protected at the URL level ({@code /admin/**} hasRole ADMIN)
 * AND at the method level ({@code @PreAuthorize}). {@code /secret} is permitted at the
 * URL level but protected <em>only</em> by {@code @PreAuthorize}, isolating method-level
 * enforcement.
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
