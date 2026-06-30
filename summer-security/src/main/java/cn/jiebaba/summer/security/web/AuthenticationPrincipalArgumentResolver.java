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
 * Resolves handler-method parameters carrying security types:
 * <ul>
 *   <li>{@code @AuthenticationPrincipal} -- injects {@link Authentication#getPrincipal()}.
 *       When the parameter type is {@link UserDetails} (or a subtype) and the current
 *       principal is a plain username string (the stateless-JWT case), the full
 *       {@code UserDetails} is reloaded via {@link UserDetailsService} so controllers
 *       can type-safely receive the user record.</li>
 *   <li>{@link Authentication} -- the current authentication (or null).</li>
 *   <li>{@link SecurityContext} -- the current security context.</li>
 * </ul>
 * Implements the {@code summer-web} {@link HandlerMethodArgumentResolver} SPI.
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
    public Object resolveArgument(Parameter parameter, Type genericType, RouteMatch match,
                                  WebRequest request, WebResponse response) {
        Authentication auth = SecurityContextHolder.getAuthentication();
        if (parameter.isAnnotationPresent(AuthenticationPrincipal.class)) {
            AuthenticationPrincipal ann = parameter.getAnnotation(AuthenticationPrincipal.class);
            Object principal = auth == null ? null : auth.getPrincipal();
            // Stateless JWT: principal is the username string. If the handler wants a
            // UserDetails, reload the full record so controllers stay type-safe.
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
                // inject null; the handler may still tolerate it (or NPE naturally)
            }
            return principal;
        }
        Class<?> type = parameter.getType();
        if (type == Authentication.class) return auth;
        if (type == SecurityContext.class) return SecurityContextHolder.getContext();
        return null;
    }
}
