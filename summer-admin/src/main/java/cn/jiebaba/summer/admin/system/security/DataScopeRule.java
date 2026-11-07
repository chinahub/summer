package cn.jiebaba.summer.admin.system.security;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 数据范围规则：{@link #unrestricted} 为 true 表示不限制；否则由可见部门集合
 * {@link #deptIds} 与是否仅含本人 {@link #includeSelf} 组成。
 * 空集合不代表"无限制"，而是"无任何权限"，应用查询时生成 1=0 永假条件，
 * 绝不省略条件导致全表泄露。
 */
public final class DataScopeRule {

    private static final DataScopeRule UNRESTRICTED = new DataScopeRule(true, Set.of(), false);
    private static final DataScopeRule DENY = new DataScopeRule(false, Set.of(), false);

    private final boolean unrestricted;
    private final Set<Long> deptIds;
    private final boolean includeSelf;

    private DataScopeRule(boolean unrestricted, Set<Long> deptIds, boolean includeSelf) {
        this.unrestricted = unrestricted;
        this.deptIds = Set.copyOf(deptIds);
        this.includeSelf = includeSelf;
    }

    /** 无限制规则：不附加任何查询条件，任何目标可见。 */
    public static DataScopeRule unrestricted() {
        return UNRESTRICTED;
    }

    /** 永拒规则：无可见部门且不含本人，应用后生成 1=0 永假条件。 */
    public static DataScopeRule deny() {
        return DENY;
    }

    /** 构造有限规则：可见部门集合 + 是否仅含本人。 */
    public static DataScopeRule of(Set<Long> deptIds, boolean includeSelf) {
        return new DataScopeRule(false, deptIds, includeSelf);
    }

    public boolean isUnrestricted() {
        return unrestricted;
    }

    public Set<Long> deptIds() {
        return deptIds;
    }

    public boolean includeSelf() {
        return includeSelf;
    }

    /**
     * 将数据范围以预编译条件（in/eq 与嵌套 OR）应用到用户查询 Wrapper：
     * 无限制不加条件；仅本人 eq 当前用户 ID；仅部门 in 部门集合；
     * 两者都有时以 (dept_id IN (...) OR user_id = ?) 嵌套 OR 组合；
     * 无任何权限时生成 1=0 永假条件，永不省略条件。
     *
     * @param wrapper        用户查询 Wrapper（不为 null）
     * @param currentUserId  当前登录用户 ID，用于 SELF 范围
     */
    public void applyToUser(LambdaQueryWrapper<SysUser> wrapper, Long currentUserId) {
        if (unrestricted) {
            return;
        }
        if (deptIds.isEmpty() && !includeSelf) {
            // 框架 in() 对空集合生成 1=0 永假条件：deny-by-default
            wrapper.in("dept_id", List.of());
            return;
        }
        if (includeSelf && deptIds.isEmpty()) {
            wrapper.eq(SysUser::getUserId, currentUserId);
            return;
        }
        if (!includeSelf) {
            wrapper.in("dept_id", deptIds);
            return;
        }
        wrapper.or(w -> {
            w.in("dept_id", deptIds);
            w.or();
            w.eq(SysUser::getUserId, currentUserId);
        });
    }

    /**
     * 按 ID 操作前校验目标用户是否在当前用户数据范围内：
     * 无限制直接放行；本人（includeSelf）放行自己的记录；
     * 部门集合包含目标部门放行；其余一律抛 403，绝不默认放行。
     *
     * @param user          目标用户实体（可为 null，视为不可见）
     * @param currentUserId 当前登录用户 ID
     * @throws BusinessException 目标不可见时抛出，状态码 403
     */
    public void assertVisible(SysUser user, Long currentUserId) {
        if (unrestricted) {
            return;
        }
        boolean visible = false;
        if (user != null) {
            if (includeSelf && Objects.equals(user.getUserId(), currentUserId)) {
                visible = true;
            }
            if (!visible && user.getDeptId() != null && deptIds.contains(user.getDeptId())) {
                visible = true;
            }
        }
        if (!visible) {
            throw new BusinessException(403, "无权访问该用户数据");
        }
    }
}
