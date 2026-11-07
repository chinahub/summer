package cn.jiebaba.summer.admin.system.log;

import cn.jiebaba.summer.core.annotation.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/** 登录日志服务：记录登录成功/失败与登出操作，解析轻量的浏览器/操作系统信息。 */
@Service
public class LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    public LoginLogService(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 写入一条登录日志：只记录用户名、IP、User-Agent 派生信息、
     * 成败状态与安全消息，绝不包含密码或验证码。
     *
     * @param userName  登录名（可能为空串，绝不记录明文密码）
     * @param ip        登录 IP
     * @param userAgent 原始 User-Agent
     * @param success   是否成功
     * @param msg       安全提示消息
     */
    public void record(String userName, String ip, String userAgent, boolean success, String msg) {
        SysLoginLog log = new SysLoginLog();
        log.setUserName(truncate(userName, 50));
        log.setIpaddr(truncate(ip, 128));
        log.setLoginLocation("");
        log.setBrowser(browserOf(userAgent));
        log.setOs(osOf(userAgent));
        log.setStatus(success ? "0" : "1");
        log.setMsg(truncate(msg, 255));
        log.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    /** 从 User-Agent 粗提取浏览器名；未知或缺失时回退 "Unknown"。 */
    private static String browserOf(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("edg/")) {
            return "Edge";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("chrome/")) {
            return "Chrome";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return "Unknown";
    }

    /** 从 User-Agent 粗提取操作系统名；未知或缺失时回退 "Unknown"。 */
    private static String osOf(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os")) {
            return "macOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad")) {
            return "iOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    /** 按列宽截断，防止超长 UA/消息写库失败。 */
    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
