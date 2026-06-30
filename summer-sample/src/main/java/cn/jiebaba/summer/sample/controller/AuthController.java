package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demonstrates authenticated endpoints and {@link AuthenticationPrincipal} injection.
 * The {@code /login} endpoint is handled by the JWT login filter (no controller needed);
 * this controller exposes {@code /me} to inspect the current principal.
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
