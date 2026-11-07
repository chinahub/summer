package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.userdetails.UserDetails;

import java.util.List;

/**
 * summer-admin 的 {@link UserDetails}：在框架接口之上携带用户 ID、
 * 资料字段、角色标识与菜单权限集合（均与 authorities 一致拆分），
 * 供 userinfo 等接口直接消费，避免二次查询。
 */
public class AdminUserDetails implements UserDetails {

    private final Long userId;
    private final Long deptId;
    private final String username;
    private final String password;
    private final String nickName;
    private final String email;
    private final String phonenumber;
    private final String sex;
    private final String avatar;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    private final List<String> roleKeys;
    private final List<String> permissions;

    public AdminUserDetails(Long userId, Long deptId, String username, String password,
                            String nickName, String email, String phonenumber, String sex,
                            String avatar, boolean enabled, List<GrantedAuthority> authorities,
                            List<String> roleKeys, List<String> permissions) {
        this.userId = userId;
        this.deptId = deptId;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.email = email;
        this.phonenumber = phonenumber;
        this.sex = sex;
        this.avatar = avatar;
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
        this.roleKeys = List.copyOf(roleKeys);
        this.permissions = List.copyOf(permissions);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getNickName() {
        return nickName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhonenumber() {
        return phonenumber;
    }

    public String getSex() {
        return sex;
    }

    public String getAvatar() {
        return avatar;
    }

    /** 角色权限字符（role_key）列表，如 {@code ["admin"]}。 */
    public List<String> getRoleKeys() {
        return roleKeys;
    }

    /** 菜单权限标识列表（不含角色标识），如 {@code ["system:user:list"]}。 */
    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public List<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
