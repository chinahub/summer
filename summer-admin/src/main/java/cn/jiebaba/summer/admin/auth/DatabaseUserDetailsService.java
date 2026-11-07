package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.admin.system.menu.MenuService;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRole;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.security.authentication.AuthenticationException;
import cn.jiebaba.summer.security.authentication.BadCredentialsException;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.security.userdetails.UserDetailsService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 数据库用户加载：按登录名读取未逻辑删除的用户，账号状态映射为
 * {@code enabled}（0 正常 / 其他停用）；角色取该用户全部启用角色的 role_key
 * （{@code ROLE_} 前缀），菜单权限经 {@link MenuService#permissionsForUser}
 * 聚合（admin 全量有效权限，普通用户启用角色菜单并集）；用户不存在统一抛
 * {@link BadCredentialsException}，避免泄露用户名是否存在。
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final MenuService menuService;

    public DatabaseUserDetailsService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                                      SysRoleMapper roleMapper, MenuService menuService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.menuService = menuService;
    }

    /**
     * 按用户名加载未删除用户：不存在抛 {@link BadCredentialsException}；
     * 组装角色（启用角色 role_key）与菜单权限 authorities。
     *
     * @param username 登录名
     * @return 携带用户 ID、资料、角色与权限的 {@link AdminUserDetails}
     * @throws AuthenticationException 用户不存在时抛凭据错误，不泄露用户名字典
     */
    @Override
    public AdminUserDetails loadUserByUsername(String username) throws AuthenticationException {
        SysUser user = findActiveUser(username);
        if (user == null) {
            throw new BadCredentialsException("Bad credentials");
        }
        List<SysRole> enabledRoles = enabledRolesOf(user.getUserId());
        Set<String> roleKeys = new LinkedHashSet<>();
        for (SysRole role : enabledRoles) {
            if (role.getRoleKey() != null && !role.getRoleKey().isBlank()) {
                roleKeys.add(role.getRoleKey());
            }
        }
        List<String> permissions = List.copyOf(menuService.permissionsForUser(user.getUserId()));

        List<GrantedAuthority> authorities = new ArrayList<>();
        roleKeys.forEach(key -> authorities.add(SimpleGrantedAuthority.roleOf(key)));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        return new AdminUserDetails(user.getUserId(), user.getDeptId(), user.getUserName(),
                user.getPassword(), user.getNickName(), user.getEmail(), user.getPhonenumber(),
                user.getSex(), user.getAvatar(), "0".equals(user.getStatus()),
                authorities, List.copyOf(roleKeys), permissions);
    }

    /** 查找未逻辑删除的用户：selectList 不自动附加逻辑删除条件，此处显式过滤 del_flag='0'。 */
    private SysUser findActiveUser(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        for (SysUser candidate : userMapper.selectList()) {
            if (Objects.equals(candidate.getUserName(), username)
                    && "0".equals(candidate.getDelFlag())) {
                return candidate;
            }
        }
        return null;
    }

    /** 用户全部启用角色（sys_user_role ∩ sys_role status=0，排除逻辑删除角色）。 */
    private List<SysRole> enabledRolesOf(Long userId) {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (SysUserRole relation : userRoleMapper.selectList()) {
            if (Objects.equals(relation.getUserId(), userId)) {
                roleIds.add(relation.getRoleId());
            }
        }
        List<SysRole> result = new ArrayList<>();
        for (SysRole role : roleMapper.selectList()) {
            if (roleIds.contains(role.getRoleId()) && "0".equals(role.getStatus())
                    && "0".equals(role.getDelFlag())) {
                result.add(role);
            }
        }
        return result;
    }
}
