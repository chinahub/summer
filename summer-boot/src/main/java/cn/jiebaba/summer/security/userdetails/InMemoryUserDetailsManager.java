package cn.jiebaba.summer.security.userdetails;

import cn.jiebaba.summer.security.authentication.AuthenticationException;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 由内存用户表支撑的 {@link UserDetailsService}。
 * <p>从如下形式的配置中加载用户：
 * <pre>
 *   summer.security.users.alice.password=$2a$10$...
 *   summer.security.users.alice.roles=ADMIN,USER
 *   summer.security.users.alice.enabled=false   # 可选，默认 true
 * </pre>
 */
public class InMemoryUserDetailsManager implements UserDetailsService {

    private static final Logger LOG = Logger.getLogger(InMemoryUserDetailsManager.class.getName());

    private final Map<String, User> users = new LinkedHashMap<>();

    public InMemoryUserDetailsManager() {}

    public InMemoryUserDetailsManager(User... users) {
        for (User u : users) this.users.put(u.getUsername(), u);
    }

    /** 注册/替换用户。 */
    public void addUser(User user) {
        users.put(user.getUsername(), user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws AuthenticationException {
        User user = users.get(username);
        if (user == null) {
            throw new AuthenticationException("User not found: " + username);
        }
        return user;
    }

    /**
     * 以前缀 {@code summer.security.users.<name>.*} 的环境属性构建实例。
     * 每个用户需要 {@code .password} 与 {@code .roles}；{@code .enabled} 可选（默认 true）。
     */
    public static InMemoryUserDetailsManager fromEnvironment(java.util.Map<String, String> props) {
        InMemoryUserDetailsManager mgr = new InMemoryUserDetailsManager();
        String prefix = "summer.security.users.";
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String key : props.keySet()) {
            if (key.startsWith(prefix) && key.endsWith(".password")) {
                names.add(key.substring(prefix.length(), key.length() - ".password".length()));
            }
        }
        for (String name : names) {
            String password = props.get(prefix + name + ".password");
            String rolesRaw = props.get(prefix + name + ".roles");
            String enabledRaw = props.get(prefix + name + ".enabled");
            if (rolesRaw == null) rolesRaw = "";
            boolean enabled = enabledRaw == null || Boolean.parseBoolean(enabledRaw.trim());
            List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(rolesRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(SimpleGrantedAuthority::roleOf)
                    .toList();
            mgr.addUser(new User(name, password, authorities, true, true, true, enabled));
        }
        if (!names.isEmpty()) {
            LOG.info("InMemoryUserDetailsManager loaded " + names.size() + " user(s): " + names);
        }
        return mgr;
    }
}
