package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.authentication.AuthenticationException;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.SecurityContext;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.security.userdetails.UserDetailsService;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.routing.RouteMatch;
import cn.jiebaba.summer.web.server.ResponseStatusException;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * 解析携带安全类型的 handler-method 参数：
 * <ul>
 *   <li>{@code @AuthenticationPrincipal} —— 注入 {@link Authentication#getPrincipal()}。
 *       当参数类型为 {@link UserDetails}（或其子类型）、且当前 principal 是普通用户名字符串
 *       （无状态 JWT 场景）时，会通过 {@link UserDetailsService} 重新加载完整
 *       {@code UserDetails}，使控制器能以类型安全的方式接收用户记录。</li>
 *   <li>{@link Authentication} —— 当前认证信息（或 null）。</li>
 *   <li>{@link SecurityContext} —— 当前安全上下文。</li>
 * </ul>
 * 实现 {@code summer-web} 的 {@link HandlerMethodArgumentResolver} SPI。
 */
public final class AuthenticationPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserDetailsService userDetailsService;

    public AuthenticationPrincipalArgumentResolver() {
        this(null);
    }

    public AuthenticationPrincipalArgumentResolver(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean supportsParameter(Parameter parameter) {
        if (parameter.isAnnotationPresent(AuthenticationPrincipal.class)) return true;
        Class<?> type = parameter.getType();
        return type == Authentication.class || type == SecurityContext.class;
    }

    @Override
    /**
     * 解析已认证主体参数：无状态 JWT 下按类型返回用户名或重新加载的 UserDetails，
     * 无主体时按 required 决定注入 null 或抛出异常。
     */
    public Object resolveArgument(Parameter parameter, Type genericType, RouteMatch match,
                                  WebRequest request, WebResponse response) {
        Authentication auth = SecurityContextHolder.getAuthentication();
        if (parameter.isAnnotationPresent(AuthenticationPrincipal.class)) {
            AuthenticationPrincipal ann = parameter.getAnnotation(AuthenticationPrincipal.class);
            Object principal = auth == null ? null : auth.getPrincipal();
            // 无状态 JWT：主体为用户名字符串。若处理器需要 UserDetails，
            // 重新加载完整记录，使控制器保持类型安全。
            if (principal instanceof String username
                    && UserDetails.class.isAssignableFrom(parameter.getType())
                    && userDetailsService != null) {
                try {
                    return userDetailsService.loadUserByUsername(username);
                } catch (AuthenticationException e) {
                    if (ann.required()) {
                        throw new ResponseStatusException(401, "Principal no longer valid: " + username, e);
                    }
                    return null;
                }
            }
            if (principal == null && ann.required()) {
                // 注入 null；处理器可能仍能容忍（或自然抛出 NPE）
            }
            return principal;
        }
        Class<?> type = parameter.getType();
        if (type == Authentication.class) return auth;
        if (type == SecurityContext.class) return SecurityContextHolder.getContext();
        return null;
    }
}
