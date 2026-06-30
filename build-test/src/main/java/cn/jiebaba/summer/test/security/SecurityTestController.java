package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.annotation.PermitAll;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Endpoints exercising permitAll, authenticated, role-based (URL + method) access. */
@RestController
public class SecurityTestController {

    @GetMapping("/public/hello")
    @PermitAll
    public Map<String, String> publicHello() {
        return Map.of("message", "open");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", principal.getUsername());
        body.put("authorities", principal.getAuthorities().toString());
        return body;
    }

    @GetMapping("/admin/info")
    @PreAuthorize(roles = {"ADMIN"})
    public Map<String, Object> adminInfo(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("area", "admin");
        body.put("user", principal.getUsername());
        return body;
    }

    /** URL-permitted but method-protected: isolates @PreAuthorize enforcement. */
    @GetMapping("/secret")
    @PreAuthorize(roles = {"ADMIN"})
    public Map<String, Object> secret(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revealed", true);
        body.put("to", principal.getUsername());
        return body;
    }

    @GetMapping("/user/profile")
    @PreAuthorize(roles = {"USER"})
    public Map<String, Object> userProfile(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", principal.getUsername());
        return body;
    }
}
