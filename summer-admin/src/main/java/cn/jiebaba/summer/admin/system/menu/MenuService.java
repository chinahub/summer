package cn.jiebaba.summer.admin.system.menu;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleMenu;
import cn.jiebaba.summer.admin.system.role.SysRoleMenuMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRole;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.security.DataScopeResolver;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.core.annotation.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 菜单服务：菜单 CRUD 与父子关系校验（类型/路由/权限标识/同级重名/防环）、
 * 按用户聚合权限标识与动态路由。user_id=1 获得全部启用菜单的权限与路由；
 * 普通用户只取其启用角色的菜单并集；停用菜单与停用角色一律排除；
 * 按钮（F）只进权限集合，不进入路由树。关系读取用 selectList() 全量后内存过滤，
 * 与 DataScopeResolver 保持一致。
 */
@Service
public class MenuService {

    private static final Pattern PERMS_PATTERN = Pattern.compile("^[a-zA-Z0-9:_-]{1,100}$");
    private static final Set<String> MENU_TYPES = Set.of("M", "C", "F");

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;

    public MenuService(SysMenuMapper menuMapper, SysRoleMapper roleMapper,
                       SysRoleMenuMapper roleMenuMapper, SysUserRoleMapper userRoleMapper,
                       SysUserMapper userMapper) {
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
    }

    /**
     * 用户权限标识集合：超级管理员（user_id=1）取全部启用菜单的权限；
     * 普通用户取其启用角色（status=0）的菜单并集中的启用菜单权限；
     * 用户不存在或无启用角色时为空。按钮权限包含在内，返回按字典序稳定排序。
     *
     * @param userId 用户 ID
     * @return 权限标识集合（永不为 null）
     */
    public Set<String> permissionsForUser(Long userId) {
        Set<String> perms = new TreeSet<>();
        for (SysMenu menu : menusForUser(userId)) {
            if (menu.getPerms() != null && !menu.getPerms().isBlank()) {
                perms.add(menu.getPerms());
            }
        }
        return perms;
    }

    /**
     * 用户动态路由树：仅含目录（M）与菜单（C），按钮剔除；按 orderNum、menuId
     * 稳定排序；父节点未授予时挂为根路由；隐藏菜单带 hidden 标记但不剔除。
     *
     * @param userId 用户 ID
     * @return 根路由列表（永不为 null）
     */
    public List<RouterVo> routersForUser(Long userId) {
        List<SysMenu> menus = new ArrayList<>();
        for (SysMenu menu : menusForUser(userId)) {
            if (!"F".equals(menu.getMenuType())) {
                menus.add(menu);
            }
        }
        menus.sort(Comparator.comparingInt(m -> m.getOrderNum() == null ? 0 : m.getOrderNum()));
        Map<Long, SysMenu> byId = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            byId.put(menu.getMenuId(), menu);
        }
        Map<Long, RouterVo> voById = new LinkedHashMap<>();
        List<RouterVo> allVos = new ArrayList<>();
        for (SysMenu menu : menus) {
            // 父节点未授予或 parentId=0 时作为独立根路由渲染（Layout + / 前缀）
            boolean standalone = menu.getParentId() == null || !byId.containsKey(menu.getParentId());
            RouterVo vo = toRouterVo(menu, standalone);
            voById.put(menu.getMenuId(), vo);
            allVos.add(vo);
        }
        List<RouterVo> roots = new ArrayList<>();
        for (int i = 0; i < menus.size(); i++) {
            SysMenu menu = menus.get(i);
            SysMenu parent = menu.getParentId() == null ? null : byId.get(menu.getParentId());
            if (parent == null || !"M".equals(parent.getMenuType())) {
                roots.add(allVos.get(i));
            } else {
                voById.get(parent.getMenuId()).getChildren().add(allVos.get(i));
            }
        }
        return roots;
    }

    /** 全部未删除菜单，按 orderNum、menuId 稳定排序（树由前端按 parentId 组装）。 */
    public List<SysMenu> list() {
        List<SysMenu> all = new ArrayList<>(menuMapper.selectList());
        all.sort(Comparator.comparingInt(m -> m.getOrderNum() == null ? 0 : m.getOrderNum()));
        return all;
    }

    /** 按 ID 查询菜单，不存在抛 404。 */
    public SysMenu get(Long menuId) {
        if (menuId == null) {
            throw new BusinessException(400, "菜单ID不能为空");
        }
        SysMenu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BusinessException(404, "菜单不存在");
        }
        return menu;
    }

    /**
     * 新增菜单：校验名称、类型（M/C/F）、父子类型匹配、路由地址、权限标识格式
     * 与同级重名；填充排序、状态、显示状态与删除标志默认值。
     *
     * @param menu 菜单实体（menuName/menuType 必填，C/M 必填 path）
     * @return 生成的菜单 ID
     */
    public Long create(SysMenu menu) {
        if (menu == null || menu.getMenuName() == null || menu.getMenuName().isBlank()) {
            throw new BusinessException(400, "菜单名称不能为空");
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        validate(menu, null);
        if (menu.getOrderNum() == null) {
            menu.setOrderNum(0);
        }
        if (menu.getStatus() == null || menu.getStatus().isBlank()) {
            menu.setStatus("0");
        }
        if (menu.getVisible() == null || menu.getVisible().isBlank()) {
            menu.setVisible("0");
        }
        menu.setDelFlag("0");
        menuMapper.insert(menu);
        return menu.getMenuId();
    }

    /**
     * 更新菜单：目标必须存在；非空字段覆盖旧值后整体校验（含禁止移动到自身
     * 或自身后代形成环）。
     *
     * @param menu 变更字段（menuId 必填；为 null 的字段保持不变）
     */
    public void update(SysMenu menu) {
        if (menu == null || menu.getMenuId() == null) {
            throw new BusinessException(400, "菜单ID不能为空");
        }
        SysMenu existing = get(menu.getMenuId());
        if (menu.getMenuName() != null) {
            existing.setMenuName(menu.getMenuName());
        }
        if (menu.getParentId() != null) {
            existing.setParentId(menu.getParentId());
        }
        if (menu.getOrderNum() != null) {
            existing.setOrderNum(menu.getOrderNum());
        }
        if (menu.getPath() != null) {
            existing.setPath(menu.getPath());
        }
        if (menu.getComponent() != null) {
            existing.setComponent(menu.getComponent());
        }
        if (menu.getQuery() != null) {
            existing.setQuery(menu.getQuery());
        }
        if (menu.getIsFrame() != null) {
            existing.setIsFrame(menu.getIsFrame());
        }
        if (menu.getIsCache() != null) {
            existing.setIsCache(menu.getIsCache());
        }
        if (menu.getMenuType() != null) {
            existing.setMenuType(menu.getMenuType());
        }
        if (menu.getVisible() != null) {
            existing.setVisible(menu.getVisible());
        }
        if (menu.getStatus() != null) {
            existing.setStatus(menu.getStatus());
        }
        if (menu.getPerms() != null) {
            existing.setPerms(menu.getPerms());
        }
        if (menu.getIcon() != null) {
            existing.setIcon(menu.getIcon());
        }
        validate(existing, existing.getMenuId());
        menuMapper.updateById(existing);
    }

    /**
     * 删除菜单（逻辑删除 del_flag 0→2）：存在未删除子菜单或被角色关联引用时
     * 抛 409 拒绝。
     *
     * @param menuId 菜单 ID
     */
    public void delete(Long menuId) {
        get(menuId);
        for (SysMenu menu : menuMapper.selectList()) {
            if (Objects.equals(menu.getParentId(), menuId)) {
                throw new BusinessException(409, "存在子菜单，不允许删除");
            }
        }
        for (SysRoleMenu relation : roleMenuMapper.selectList()) {
            if (Objects.equals(relation.getMenuId(), menuId)) {
                throw new BusinessException(409, "菜单已分配给角色，不允许删除");
            }
        }
        menuMapper.deleteById(menuId);
    }

    /**
     * 用户可见菜单集合：超级管理员返回全部启用菜单；普通用户返回其启用角色
     * （sys_user_role ∩ sys_role status=0）经 sys_role_menu 关联的启用菜单并集；
     * 用户不存在或无启用角色时返回空列表（拒绝默认，不扩大权限）。
     */
    private List<SysMenu> menusForUser(Long userId) {
        List<SysMenu> all = menuMapper.selectList();
        if (userId != null && userId == DataScopeResolver.SUPER_ADMIN_ID) {
            return filterEnabled(all);
        }
        if (userId == null || userMapper.selectById(userId) == null) {
            return List.of();
        }
        Set<Long> roleIds = new HashSet<>();
        for (SysUserRole userRole : userRoleMapper.selectList()) {
            if (Objects.equals(userRole.getUserId(), userId)) {
                roleIds.add(userRole.getRoleId());
            }
        }
        List<SysRole> enabledRoles = new ArrayList<>();
        for (SysRole role : roleMapper.selectList()) {
            if (roleIds.contains(role.getRoleId()) && "0".equals(role.getStatus())) {
                enabledRoles.add(role);
            }
        }
        if (enabledRoles.isEmpty()) {
            return List.of();
        }
        Set<Long> enabledRoleIds = new HashSet<>();
        for (SysRole role : enabledRoles) {
            enabledRoleIds.add(role.getRoleId());
        }
        Set<Long> menuIds = new HashSet<>();
        for (SysRoleMenu relation : roleMenuMapper.selectList()) {
            if (enabledRoleIds.contains(relation.getRoleId())) {
                menuIds.add(relation.getMenuId());
            }
        }
        List<SysMenu> result = new ArrayList<>();
        for (SysMenu menu : all) {
            if (menuIds.contains(menu.getMenuId())) {
                result.add(menu);
            }
        }
        return filterEnabled(result);
    }

    /** 过滤出启用（status=0）菜单。 */
    private static List<SysMenu> filterEnabled(List<SysMenu> menus) {
        List<SysMenu> enabled = new ArrayList<>();
        for (SysMenu menu : menus) {
            if ("0".equals(menu.getStatus())) {
                enabled.add(menu);
            }
        }
        return enabled;
    }

    /**
     * 将菜单转换为路由节点：独立根节点（父未授予或 parentId=0）路径加 / 前缀且
     * component 为 Layout；隐藏菜单置 hidden；元数据携带标题、图标与缓存策略。
     */
    private static RouterVo toRouterVo(SysMenu menu, boolean standalone) {
        RouterVo vo = new RouterVo();
        vo.setName(routeName(menu));
        vo.setPath(standalone ? "/" + nullToEmpty(menu.getPath()) : nullToEmpty(menu.getPath()));
        vo.setComponent(standalone ? "Layout" : menu.getComponent());
        vo.setHidden("1".equals(menu.getVisible()));
        vo.setQuery(menu.getQuery());
        if ("M".equals(menu.getMenuType()) && standalone) {
            vo.setRedirect("noRedirect");
        }
        RouterVo.Meta meta = new RouterVo.Meta();
        meta.setTitle(menu.getMenuName());
        meta.setIcon(menu.getIcon());
        meta.setNoCache(menu.getIsCache() != null && menu.getIsCache() == 1);
        vo.setMeta(meta);
        return vo;
    }

    /** 路由名称：取路由地址首字母大写；为空时退化为 Menu+ID，保证前端 name 稳定唯一。 */
    private static String routeName(SysMenu menu) {
        String path = menu.getPath();
        if (path == null || path.isBlank()) {
            return "Menu" + menu.getMenuId();
        }
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        return cleaned.isEmpty() ? "Menu" + menu.getMenuId()
                : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    /** 整体校验菜单：类型枚举、父子类型匹配（含防环）、路由地址与权限标识格式、同级重名。 */
    private void validate(SysMenu menu, Long excludeId) {
        String type = menu.getMenuType();
        if (type == null || !MENU_TYPES.contains(type)) {
            throw new BusinessException(400, "菜单类型必须是 M（目录）、C（菜单）或 F（按钮）");
        }
        if (!"F".equals(type) && (menu.getPath() == null || menu.getPath().isBlank())) {
            throw new BusinessException(400, "路由地址不能为空");
        }
        String perms = menu.getPerms();
        if (perms != null && !perms.isBlank() && !PERMS_PATTERN.matcher(perms).matches()) {
            throw new BusinessException(400, "权限标识格式不正确（仅允许字母、数字、冒号、下划线与连字符）");
        }
        Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
        SysMenu parent = null;
        if (parentId != 0L) {
            parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(404, "父菜单不存在");
            }
            if ("F".equals(parent.getMenuType())) {
                throw new BusinessException(409, "按钮不能作为父菜单");
            }
        }
        if ("F".equals(type) && (parent == null || !"C".equals(parent.getMenuType()))) {
            throw new BusinessException(409, "按钮的上级必须是菜单");
        }
        if (("M".equals(type) || "C".equals(type)) && parent != null
                && !"M".equals(parent.getMenuType())) {
            throw new BusinessException(409, "目录和菜单的上级必须是目录");
        }
        if (excludeId != null) {
            assertNotCycle(excludeId, parentId);
        }
        for (SysMenu other : menuMapper.selectList()) {
            if (Objects.equals(other.getMenuId(), excludeId)) {
                continue;
            }
            if (Objects.equals(other.getParentId(), parentId)
                    && menu.getMenuName().equals(other.getMenuName())) {
                throw new BusinessException(409, "同级菜单名称已存在");
            }
        }
    }

    /** 沿父链上溯：新父菜单若为自身或自身后代则形成环，抛 409；脏数据成环时防御性终止。 */
    private void assertNotCycle(Long menuId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (Objects.equals(cursor, menuId)) {
                throw new BusinessException(409, "不能选择自己或自己的子菜单作为父菜单");
            }
            if (!visited.add(cursor)) {
                break;
            }
            SysMenu current = menuMapper.selectById(cursor);
            if (current == null) {
                break;
            }
            cursor = current.getParentId();
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
