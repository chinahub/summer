package cn.jiebaba.summer.admin.system.dept;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.security.DataScopeRule;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 部门服务：维护部门树父子关系、ancestors 路径与排序；删除前校验
 * 下级部门与归属用户；提供按数据范围过滤的可见部门列表。
 */
@Service
public class DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public DeptService(SysDeptMapper deptMapper, SysUserMapper userMapper) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    /** 全部未删除部门，部门树由前端基于 parentId 组装。 */
    public List<SysDept> list() {
        return deptMapper.selectList();
    }

    /** 按 ID 查询部门，不存在抛 404。 */
    public SysDept get(Long deptId) {
        if (deptId == null) {
            throw new BusinessException(400, "部门ID不能为空");
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }
        return dept;
    }

    /**
     * 按数据范围过滤可见部门：无限制返回全部；永拒返回空列表；
     * 仅本人范围附带当前用户所在部门；其余按可见部门集合过滤。
     *
     * @param rule          当前用户数据范围规则
     * @param currentUserId 当前登录用户 ID（SELF 范围定位本人部门）
     * @return 可见部门列表
     */
    public List<SysDept> visibleList(DataScopeRule rule, Long currentUserId) {
        List<SysDept> all = list();
        if (rule.isUnrestricted()) {
            return all;
        }
        if (rule.deptIds().isEmpty() && !rule.includeSelf()) {
            return List.of();
        }
        Set<Long> visibleIds = new HashSet<>(rule.deptIds());
        if (rule.includeSelf() && currentUserId != null) {
            SysUser user = userMapper.selectById(currentUserId);
            if (user != null && user.getDeptId() != null) {
                visibleIds.add(user.getDeptId());
            }
        }
        List<SysDept> result = new ArrayList<>();
        for (SysDept dept : all) {
            if (visibleIds.contains(dept.getDeptId())) {
                result.add(dept);
            }
        }
        return result;
    }

    /**
     * 新增部门：校验父部门存在并生成 ancestors 路径（根部门为 "0"），
     * 填充排序、状态与删除标志默认值。
     *
     * @param dept 部门实体（deptName 必填，parentId 为空视为根）
     * @return 生成的部门 ID
     */
    public Long create(SysDept dept) {
        if (dept == null || dept.getDeptName() == null || dept.getDeptName().isBlank()) {
            throw new BusinessException(400, "部门名称不能为空");
        }
        Long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
        dept.setParentId(parentId);
        dept.setAncestors(resolveAncestors(parentId));
        if (dept.getOrderNum() == null) {
            dept.setOrderNum(0);
        }
        if (dept.getStatus() == null || dept.getStatus().isBlank()) {
            dept.setStatus("0");
        }
        dept.setDelFlag("0");
        deptMapper.insert(dept);
        return dept.getDeptId();
    }

    /**
     * 更新部门：禁止移动到自身或自身后代形成环；移动后同步将全部后代的
     * ancestors 前缀替换为新路径，整个操作在同一事务内完成。
     *
     * @param dept 变更字段（deptId 必填；为 null 的字段保持不变）
     */
    @Transactional
    public void update(SysDept dept) {
        if (dept == null || dept.getDeptId() == null) {
            throw new BusinessException(400, "部门ID不能为空");
        }
        SysDept existing = get(dept.getDeptId());
        Long parentId = dept.getParentId() == null ? existing.getParentId() : dept.getParentId();
        assertNotCycle(existing.getDeptId(), parentId);
        String oldPrefix = existing.getAncestors() + "," + existing.getDeptId();
        String newAncestors = resolveAncestors(parentId);
        existing.setParentId(parentId);
        existing.setAncestors(newAncestors);
        if (dept.getDeptName() != null && !dept.getDeptName().isBlank()) {
            existing.setDeptName(dept.getDeptName());
        }
        if (dept.getOrderNum() != null) {
            existing.setOrderNum(dept.getOrderNum());
        }
        if (dept.getLeader() != null) {
            existing.setLeader(dept.getLeader());
        }
        if (dept.getPhone() != null) {
            existing.setPhone(dept.getPhone());
        }
        if (dept.getEmail() != null) {
            existing.setEmail(dept.getEmail());
        }
        if (dept.getStatus() != null && !dept.getStatus().isBlank()) {
            existing.setStatus(dept.getStatus());
        }
        deptMapper.updateById(existing);
        for (SysDept child : deptMapper.selectList()) {
            if (Objects.equals(child.getDeptId(), existing.getDeptId())) {
                continue;
            }
            String ancestors = child.getAncestors();
            if (ancestors != null
                    && (ancestors.equals(oldPrefix) || ancestors.startsWith(oldPrefix + ","))) {
                // 后代路径 = 新父路径 + "," + 自身ID + 原路径中自身ID之后的部分
                child.setAncestors(newAncestors + "," + existing.getDeptId()
                        + ancestors.substring(oldPrefix.length()));
                deptMapper.updateById(child);
            }
        }
    }

    /**
     * 删除部门（逻辑删除 del_flag 0→2）：存在下级部门或归属用户时抛 409 拒绝。
     *
     * @param deptId 部门 ID
     */
    @Transactional
    public void delete(Long deptId) {
        get(deptId);
        for (SysDept dept : deptMapper.selectList()) {
            if (Objects.equals(dept.getParentId(), deptId)) {
                throw new BusinessException(409, "存在下级部门，不允许删除");
            }
        }
        for (SysUser user : userMapper.selectList()) {
            if (Objects.equals(user.getDeptId(), deptId)) {
                throw new BusinessException(409, "部门存在用户，不允许删除");
            }
        }
        deptMapper.deleteById(deptId);
    }

    /** 计算父部门的 ancestors 路径：根（parentId=0）为 "0"，否则父路径 + "," + 父ID。 */
    private String resolveAncestors(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "0";
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(404, "父部门不存在");
        }
        return parent.getAncestors() + "," + parentId;
    }

    /** 沿父链上溯：新父部门若为自身或自身后代则形成环，抛 409；脏数据成环时防御性终止。 */
    private void assertNotCycle(Long deptId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (Objects.equals(cursor, deptId)) {
                throw new BusinessException(409, "不能选择自己或自己的子部门作为父部门");
            }
            if (!visited.add(cursor)) {
                break;
            }
            SysDept current = deptMapper.selectById(cursor);
            if (current == null) {
                break;
            }
            cursor = current.getParentId();
        }
    }
}
