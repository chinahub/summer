package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.admin.auth.captcha.CaptchaService;
import cn.jiebaba.summer.admin.auth.dto.LoginRequest;
import cn.jiebaba.summer.admin.auth.dto.LoginResult;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.validation.RequestValidator;
import cn.jiebaba.summer.admin.system.log.LoginLogService;
import cn.jiebaba.summer.admin.system.menu.MenuService;
import cn.jiebaba.summer.admin.system.menu.RouterVo;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.security.authentication.AccountStatusException;
import cn.jiebaba.summer.security.authentication.AuthenticationException;
import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.security.jwt.JwtClaims;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.userdetails.UserDetailsService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务：先校验一次性验证码，再经 {@link AuthenticationManager} 认证数据库用户，
 * 成功后签发 {@code typ=access} 的 JWT（默认 TTL 7200 秒，可配置）。
 * 登录成功、凭证失败、验证码失败与账号停用均写登录日志，绝不记录密码/验证码。
 * 登出仅记录操作，不做服务端令牌撤销（无状态边界）。
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    // 依赖 UserDetailsService 接口而非实现：该实现会被操作日志切面 JDK 代理，
    // 按接口注入才能保证拿到代理 Bean；本应用唯一实现即 DatabaseUserDetailsService
    private final UserDetailsService userDetailsService;
    private final MenuService menuService;
    private final long tokenTtlSeconds;

    public AuthService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                       CaptchaService captchaService, LoginLogService loginLogService,
                       UserDetailsService userDetailsService, MenuService menuService,
                       Environment env) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.captchaService = captchaService;
        this.loginLogService = loginLogService;
        this.userDetailsService = userDetailsService;
        this.menuService = menuService;
        this.tokenTtlSeconds = env.getProperty(
                "summer.security.jwt.access-token-ttl", Long.class, 7200L);
    }

    /**
     * 登录：显式校验账号密码 → 校验并消费验证码 → AuthenticationManager 认证 →
     * 签发访问令牌；全过程写登录日志，失败按类型映射为 400（验证码）/401（凭据/停用）。
     *
     * @param request   登录请求（username/password 必填）
     * @param ip        登录 IP（写日志）
     * @param userAgent 原始 User-Agent（写日志）
     * @return 令牌结果
     * @throws BusinessException 验证码错误 400；凭据错误/账号停用 401
     */
    public LoginResult login(LoginRequest request, String ip, String userAgent) {
        RequestValidator.requireValid(request);
        if (!captchaService.verify(request.getUuid(), request.getCode())) {
            loginLogService.record(request.getUsername(), ip, userAgent, false, "验证码错误或已失效");
            throw new BusinessException(400, "验证码错误或已失效");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AccountStatusException e) {
            loginLogService.record(request.getUsername(), ip, userAgent, false, "账号已停用");
            throw new BusinessException(401, "账号已停用");
        } catch (AuthenticationException e) {
            loginLogService.record(request.getUsername(), ip, userAgent, false, "用户名或密码错误");
            throw new BusinessException(401, "用户名或密码错误");
        }
        String username = authentication.getName();
        loginLogService.record(username, ip, userAgent, true, "登录成功");
        long now = Instant.now().getEpochSecond();
        JwtClaims claims = JwtClaims.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now + tokenTtlSeconds)
                .type(JwtClaims.TYPE_ACCESS)
                .authorities(authentication.getAuthorities())
                .build();
        return new LoginResult(jwtEncoder.encode(claims), "Bearer", tokenTtlSeconds);
    }

    /**
     * 当前用户资料：用户基本信息（不含密码）、角色权限字符与菜单权限标识。
     * 用户已被删除时按 401 处理（登录状态失效）。
     *
     * @param username 当前登录名
     * @return userinfo 数据负载
     */
    public Map<String, Object> userinfo(String username) {
        AdminUserDetails details = requireCurrentUser(username);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", details.getUserId());
        user.put("userName", details.getUsername());
        user.put("nickName", details.getNickName());
        user.put("deptId", details.getDeptId());
        user.put("email", details.getEmail());
        user.put("phonenumber", details.getPhonenumber());
        user.put("sex", details.getSex());
        user.put("avatar", details.getAvatar());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("roles", details.getRoleKeys());
        data.put("permissions", details.getPermissions());
        return data;
    }

    /** 当前用户动态路由：仅含目录与菜单，按钮不进路由但权限标识已下发。 */
    public List<RouterVo> routers(String username) {
        return menuService.routersForUser(requireCurrentUser(username).getUserId());
    }

    /** 登出：仅记录操作日志；JWT 无状态，不做服务端撤销。 */
    public void logout(String username, String ip, String userAgent) {
        loginLogService.record(username, ip, userAgent, true, "退出成功");
    }

    /** 重新加载当前用户；用户不存在（令牌仍有效但账号已删）按 401 处理。 */
    private AdminUserDetails requireCurrentUser(String username) {
        try {
            // 唯一实现为 DatabaseUserDetailsService（协变返回 AdminUserDetails），转换安全
            return (AdminUserDetails) userDetailsService.loadUserByUsername(username);
        } catch (AuthenticationException e) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
    }
}
