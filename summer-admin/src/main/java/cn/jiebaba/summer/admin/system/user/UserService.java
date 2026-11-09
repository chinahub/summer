package cn.jiebaba.summer.admin.system.user;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLog;
import cn.jiebaba.summer.admin.common.validation.RequestValidator;
import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.post.SysPost;
import cn.jiebaba.summer.admin.system.post.SysPostMapper;
import cn.jiebaba.summer.admin.system.post.SysUserPost;
import cn.jiebaba.summer.admin.system.post.SysUserPostMapper;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRole;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.security.DataScopeResolver;
import cn.jiebaba.summer.admin.system.security.DataScopeRule;
import cn.jiebaba.summer.admin.system.user.dto.ChangePasswordRequest;
import cn.jiebaba.summer.admin.system.user.dto.CreateUserRequest;
import cn.jiebaba.summer.admin.system.user.dto.ResetPasswordRequest;
import cn.jiebaba.summer.admin.system.user.dto.UpdateProfileRequest;
import cn.jiebaba.summer.admin.system.user.dto.UpdateUserRequest;
import cn.jiebaba.summer.admin.system.user.dto.UserVo;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.data.transaction.Transactional;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户服务：用户分页/详情/新增/修改/删除/状态/重置密码，角色岗位关系分配，
 * 以及个人中心资料与密码维护。
 * 数据权限由 {@link DataScopeResolver} 在服务端解析并同时约束列表查询与
 * 按 ID 操作（{@link DataScopeRule#assertVisible}），绝不信任客户端范围传参；
 * 关系表（sys_user_role/sys_user_post）为物理删除表且 Central BaseMapper
 * 缺少条件删除，统一经 {@link SqlExecutor} 预编译参数删除；关系替换整体在
 * 一个事务内完成（先清旧关系再校验并写入新关系），任何一步失败整体回滚。
 * 全程只输出 {@link UserVo}（不含密码），密码仅在新增/重置/改密入口以
 * BCrypt 编码落库。
 */
@Service
public class UserService {

    /** 内置超级管理员固定 user_id=1（RuoYi 惯例），禁止删除/禁用/重置密码。 */
    public static final long SUPER_ADMIN_ID = 1L;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final SysDeptMapper deptMapper;
    private final DataScopeResolver dataScopeResolver;
    private final SqlExecutor sqlExecutor;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                       SysUserPostMapper userPostMapper, SysRoleMapper roleMapper,
                       SysPostMapper postMapper, SysDeptMapper deptMapper,
                       DataScopeResolver dataScopeResolver, SqlExecutor sqlExecutor,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.userPostMapper = userPostMapper;
        this.roleMapper = roleMapper;
        this.postMapper = postMapper;
        this.deptMapper = deptMapper;
        this.dataScopeResolver = dataScopeResolver;
        this.sqlExecutor = sqlExecutor;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户列表（无部门过滤）：等价于 deptId 传 null 的完整形态，
     * 保留签名兼容既有调用方。
     */
    public IPage<UserVo> page(PageQuery query, Long currentUserId,
                              String userName, String phonenumber, String status) {
        return page(query, currentUserId, userName, phonenumber, status, null);
    }

    /**
     * 分页查询用户列表：登录名/手机号模糊、状态精确过滤，按用户 ID 升序；
     * 数据范围经服务端解析后以预编译条件附加到查询；deptId 为选中部门时
     * 仅返回该部门子树（含自身）内的用户，且与数据范围条件按 AND 组合——
     * 部门过滤只会收窄、绝不放宽数据范围。
     * 注意框架 Wrapper 查询不自动附加逻辑删除条件，需显式声明 del_flag = '0'。
     *
     * @param query         分页参数
     * @param currentUserId 当前登录用户 ID（数据范围基准，来自认证主体）
     * @param userName      登录名模糊词（可空）
     * @param phonenumber   手机号模糊词（可空）
     * @param status        状态过滤（可空）
     * @param deptId        部门树筛选（可空；匹配该部门子树，含自身）
     * @return 用户 VO 分页结果（部门/角色/岗位名称批量组装）
     */
    public IPage<UserVo> page(PageQuery query, Long currentUserId,
                              String userName, String phonenumber, String status, Long deptId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        if (userName != null && !userName.isBlank()) {
            wrapper.like(SysUser::getUserName, userName);
        }
        if (phonenumber != null && !phonenumber.isBlank()) {
            wrapper.like(SysUser::getPhonenumber, phonenumber);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysUser::getStatus, status);
        }
        dataScopeResolver.resolve(currentUserId).applyToUser(wrapper, currentUserId);
        if (deptId != null) {
            // 框架 LambdaQueryWrapper 未提供 lambda 版 in：直接给预编译列名
            wrapper.in("dept_id", deptSubtreeIds(deptId));
        }
        wrapper.orderByAsc(SysUser::getUserId);
        IPage<SysUser> page = userMapper.selectPage(query.toPage(), wrapper);
        return mapPage(page);
    }

    /**
     * 计算部门子树（含自身）的全部部门 ID：基于 sys_dept.ancestors 祖级路径
     * 匹配后代，与部门移动时维护 ancestors 的逻辑保持一致。
     * 必须按逗号分段后精确比对：deptId=1 不得因 "0,11" 包含子串 ",1"
     * 而误纳部门 11 的后代（子串匹配会把 11 的子树错挂到 1 下）。
     *
     * @param deptId 选中部门 ID
     * @return 该部门及全部后代的 ID 集合
     */
    private Set<Long> deptSubtreeIds(Long deptId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(deptId);
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        String target = String.valueOf(deptId);
        for (SysDept dept : deptMapper.selectList(wrapper)) {
            String ancestors = dept.getAncestors();
            if (ancestors == null || ancestors.isBlank()) {
                continue;
            }
            for (String segment : ancestors.split(",")) {
                if (segment.trim().equals(target)) {
                    ids.add(dept.getDeptId());
                    break;
                }
            }
        }
        return ids;
    }

    /**
     * 按 ID 查询用户详情：目标必须未逻辑删除且在数据范围内，
     * 返回批量组装部门/角色/岗位名称的 VO。
     *
     * @param userId        目标用户 ID
     * @param currentUserId 当前登录用户 ID
     * @return 用户 VO
     * @throws BusinessException 目标不存在 404，越权 403
     */
    public UserVo get(Long userId, Long currentUserId) {
        SysUser user = requireActiveUser(userId);
        ruleOf(currentUserId).assertVisible(user, currentUserId);
        return toVo(user);
    }

    /**
     * 新增用户：登录名在未删除用户中唯一；归属部门、角色、岗位逐一校验存在；
     * 目标部门必须在当前用户数据范围内；密码 BCrypt 编码落库。
     * 用户与角色/岗位关系在同一事务内写入，任何一步失败整体回滚。
     *
     * @param request       新增请求（登录名/昵称/密码/部门必填）
     * @param currentUserId 当前登录用户 ID
     * @return 生成的用户 ID
     */
    @OperLog(module = "用户管理", action = "新增")
    @Transactional
    public Long create(CreateUserRequest request, Long currentUserId) {
        RequestValidator.requireValid(request);
        assertUserNameUnique(request.getUserName(), null);
        requireDept(request.getDeptId());
        Set<Long> roleIds = normalized(request.getRoleIds());
        Set<Long> postIds = normalized(request.getPostIds());
        requireRolesExist(roleIds);
        requirePostsExist(postIds);

        SysUser user = new SysUser();
        user.setUserName(request.getUserName());
        user.setNickName(request.getNickName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDeptId(request.getDeptId());
        user.setEmail(request.getEmail());
        user.setPhonenumber(request.getPhonenumber());
        user.setSex(request.getSex() == null || request.getSex().isBlank() ? "2" : request.getSex());
        user.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? "0" : request.getStatus());
        user.setDelFlag("0");
        ruleOf(currentUserId).assertVisible(user, currentUserId);

        userMapper.insert(user);
        insertRelations(user.getUserId(), roleIds, postIds);
        return user.getUserId();
    }

    /**
     * 修改用户：非空字段覆盖旧值，登录名变更保持唯一（排除自身）；
     * roleIds/postIds 为 null 保持原关系，传入集合表示整体替换；
     * 修改后的归属部门仍必须在数据范围内。关系替换与用户更新在同一事务内完成。
     *
     * @param request       变更请求（userId 必填）
     * @param currentUserId 当前登录用户 ID
     */
    @OperLog(module = "用户管理", action = "修改")
    @Transactional
    public void update(UpdateUserRequest request, Long currentUserId) {
        RequestValidator.requireValid(request);
        SysUser existing = requireActiveUser(request.getUserId());
        ruleOf(currentUserId).assertVisible(existing, currentUserId);
        // 在副本上合并变更：任何校验/越权失败都不会污染内存中的实体引用
        SysUser merged = copyOf(existing);
        if (request.getUserName() != null && !request.getUserName().isBlank()) {
            assertUserNameUnique(request.getUserName(), existing.getUserId());
            merged.setUserName(request.getUserName());
        }
        if (request.getDeptId() != null) {
            requireDept(request.getDeptId());
            merged.setDeptId(request.getDeptId());
        }
        if (request.getNickName() != null) {
            merged.setNickName(request.getNickName());
        }
        if (request.getEmail() != null) {
            merged.setEmail(request.getEmail());
        }
        if (request.getPhonenumber() != null) {
            merged.setPhonenumber(request.getPhonenumber());
        }
        if (request.getSex() != null) {
            merged.setSex(request.getSex());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            assertNotForbiddenTarget(existing.getUserId(), currentUserId, "停用");
            merged.setStatus(request.getStatus());
        }
        // 部门移动后的目标仍必须在数据范围内，绝不越权搬运用户
        ruleOf(currentUserId).assertVisible(merged, currentUserId);

        userMapper.updateById(merged);
        if (request.getRoleIds() != null) {
            Set<Long> roleIds = normalized(request.getRoleIds());
            requireRolesExist(roleIds);
            removeUserRoles(existing.getUserId());
            insertUserRoles(existing.getUserId(), roleIds);
        }
        if (request.getPostIds() != null) {
            Set<Long> postIds = normalized(request.getPostIds());
            requirePostsExist(postIds);
            removeUserPosts(existing.getUserId());
            insertUserPosts(existing.getUserId(), postIds);
        }
    }

    /** 浅拷贝用户实体（含审计字段）：更新先合并到副本再落库，失败路径不改动原对象。 */
    private static SysUser copyOf(SysUser source) {
        SysUser copy = new SysUser();
        copy.setUserId(source.getUserId());
        copy.setDeptId(source.getDeptId());
        copy.setUserName(source.getUserName());
        copy.setNickName(source.getNickName());
        copy.setEmail(source.getEmail());
        copy.setPhonenumber(source.getPhonenumber());
        copy.setSex(source.getSex());
        copy.setAvatar(source.getAvatar());
        copy.setPassword(source.getPassword());
        copy.setStatus(source.getStatus());
        copy.setDelFlag(source.getDelFlag());
        copy.setLoginIp(source.getLoginIp());
        copy.setLoginDate(source.getLoginDate());
        copy.setCreateBy(source.getCreateBy());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateBy(source.getUpdateBy());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    /**
     * 删除用户（逻辑删除 del_flag 0→2）：user_id=1 与当前用户自身禁止删除；
     * 目标必须在数据范围内；删除同时清理角色/岗位关系，同一事务内完成。
     *
     * @param userId        目标用户 ID
     * @param currentUserId 当前登录用户 ID
     */
    @OperLog(module = "用户管理", action = "删除")
    @Transactional
    public void delete(Long userId, Long currentUserId) {
        assertNotForbiddenTarget(userId, currentUserId, "删除");
        SysUser user = requireActiveUser(userId);
        ruleOf(currentUserId).assertVisible(user, currentUserId);
        removeUserRoles(userId);
        removeUserPosts(userId);
        userMapper.deleteById(userId);
    }

    /**
     * 修改用户状态（0正常 1停用）：user_id=1 与当前用户自身禁止停用/启停；
     * 目标必须在数据范围内。
     *
     * @param userId        目标用户 ID
     * @param status        目标状态（仅允许 0/1）
     * @param currentUserId 当前登录用户 ID
     */
    @OperLog(module = "用户管理", action = "修改")
    public void updateStatus(Long userId, String status, Long currentUserId) {
        if (status == null || (!"0".equals(status) && !"1".equals(status))) {
            throw new BusinessException(400, "状态仅支持 0（正常）或 1（停用）");
        }
        assertNotForbiddenTarget(userId, currentUserId, "修改状态");
        SysUser user = requireActiveUser(userId);
        ruleOf(currentUserId).assertVisible(user, currentUserId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /**
     * 管理端重置密码：user_id=1 与当前用户自身禁止（本人改密必须走
     * 个人中心并验证原密码）；目标必须在数据范围内；新密码 BCrypt 编码落库。
     *
     * @param userId        目标用户 ID
     * @param newPassword   新密码（明文仅用于编码，绝不落库/回显）
     * @param currentUserId 当前登录用户 ID
     */
    @OperLog(module = "用户管理", action = "重置密码")
    public void resetPassword(Long userId, String newPassword, Long currentUserId) {
        ResetPasswordRequest check = new ResetPasswordRequest();
        check.setPassword(newPassword);
        RequestValidator.requireValid(check);
        assertNotForbiddenTarget(userId, currentUserId, "重置密码");
        SysUser user = requireActiveUser(userId);
        ruleOf(currentUserId).assertVisible(user, currentUserId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    /** 当前用户资料：本人账号直接读取，不附加数据范围限制（范围约束针对他人数据）。 */
    public UserVo profile(Long userId) {
        return toVo(requireActiveUser(userId));
    }

    /**
     * 个人中心更新资料：仅白名单字段（昵称/邮箱/手机号/性别）允许修改，
     * 登录名、部门、状态、角色等一律不动；只能操作本人账号。
     *
     * @param userId  当前登录用户 ID（来自认证主体）
     * @param request 资料变更（字段均为可选）
     */
    @OperLog(module = "个人中心", action = "修改")
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        RequestValidator.requireValid(request);
        SysUser user = requireActiveUser(userId);
        if (request.getNickName() != null) {
            user.setNickName(request.getNickName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhonenumber() != null) {
            user.setPhonenumber(request.getPhonenumber());
        }
        if (request.getSex() != null) {
            user.setSex(request.getSex());
        }
        userMapper.updateById(user);
    }

    /**
     * 个人中心修改密码：必须验证原密码（BCrypt 匹配），通过后新密码
     * 重新 BCrypt 编码落库；只能操作本人账号。
     *
     * @param userId  当前登录用户 ID（来自认证主体）
     * @param request 改密请求（原密码/新密码必填）
     */
    @OperLog(module = "个人中心", action = "修改")
    public void changePassword(Long userId, ChangePasswordRequest request) {
        RequestValidator.requireValid(request);
        SysUser user = requireActiveUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(409, "原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    /** 读取目标用户：不存在或已逻辑删除统一 404，绝不泄露账号是否存在之外的信息。 */
    private SysUser requireActiveUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"0".equals(user.getDelFlag())) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /** 当前用户数据范围规则（服务端解析）。 */
    private DataScopeRule ruleOf(Long currentUserId) {
        return dataScopeResolver.resolve(currentUserId);
    }

    /** user_id=1 与当前用户自身为禁止操作目标。 */
    private static void assertNotForbiddenTarget(Long userId, Long currentUserId, String action) {
        if (userId != null && userId == SUPER_ADMIN_ID) {
            throw new BusinessException(409, "内置超级管理员不允许" + action);
        }
        if (userId != null && Objects.equals(userId, currentUserId)) {
            throw new BusinessException(409, "不允许" + action + "当前登录账号");
        }
    }

    /** 校验登录名在未删除用户中唯一（excludeId 用于更新时排除自身）。 */
    private void assertUserNameUnique(String userName, Long excludeId) {
        for (SysUser other : userMapper.selectList()) {
            if (Objects.equals(other.getUserId(), excludeId)) {
                continue;
            }
            if (userName.equals(other.getUserName())) {
                throw new BusinessException(409, "登录名已存在");
            }
        }
    }

    /** 校验部门存在（未逻辑删除）。 */
    private void requireDept(Long deptId) {
        SysDept dept = deptId == null ? null : deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在: " + deptId);
        }
    }

    /** 校验角色集合全部存在（未逻辑删除），任一缺失抛 404。 */
    private void requireRolesExist(Set<Long> roleIds) {
        for (Long roleId : roleIds) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException(404, "角色不存在: " + roleId);
            }
        }
    }

    /** 校验岗位集合全部存在（未逻辑删除），任一缺失抛 404。 */
    private void requirePostsExist(Set<Long> postIds) {
        for (Long postId : postIds) {
            SysPost post = postMapper.selectById(postId);
            if (post == null) {
                throw new BusinessException(404, "岗位不存在: " + postId);
            }
        }
    }

    /** 归一化关系集合：null 视为空集合（清空语义由调用方决定）。 */
    private static Set<Long> normalized(Set<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    /** 一次性写入用户的角色与岗位关系（均已提前校验存在）。 */
    private void insertRelations(Long userId, Set<Long> roleIds, Set<Long> postIds) {
        insertUserRoles(userId, roleIds);
        insertUserPosts(userId, postIds);
    }

    private void insertUserRoles(Long userId, Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysUserRole> relations = new ArrayList<>();
        for (Long roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relations.add(relation);
        }
        userRoleMapper.insertBatch(relations);
    }

    private void insertUserPosts(Long userId, Set<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        List<SysUserPost> relations = new ArrayList<>();
        for (Long postId : postIds) {
            SysUserPost relation = new SysUserPost();
            relation.setUserId(userId);
            relation.setPostId(postId);
            relations.add(relation);
        }
        userPostMapper.insertBatch(relations);
    }

    /**
     * 物理删除用户的全部角色关联：Central BaseMapper 缺少条件删除，
     * 使用预编译参数经 SqlExecutor 执行，杜绝 SQL 拼接注入。
     */
    protected int removeUserRoles(Long userId) {
        return sqlExecutor.update(new SqlBuilder.Sql(
                "DELETE FROM sys_user_role WHERE user_id = ?", List.of(userId)));
    }

    /** 物理删除用户的全部岗位关联，语义同 {@link #removeUserRoles(Long)}。 */
    protected int removeUserPosts(Long userId) {
        return sqlExecutor.update(new SqlBuilder.Sql(
                "DELETE FROM sys_user_post WHERE user_id = ?", List.of(userId)));
    }

    /**
     * 批量组装用户 VO 列表：一次性加载部门/角色/岗位映射，
     * 再按用户填充展示名字段，避免逐行回表查询。
     */
    private List<UserVo> toVos(List<SysUser> users) {
        Set<Long> deptIds = new LinkedHashSet<>();
        Set<Long> roleIds = new LinkedHashSet<>();
        Set<Long> postIds = new LinkedHashSet<>();
        Set<Long> userIds = new LinkedHashSet<>();
        for (SysUser user : users) {
            userIds.add(user.getUserId());
            if (user.getDeptId() != null) {
                deptIds.add(user.getDeptId());
            }
        }
        for (SysUserRole relation : userRoleMapper.selectList()) {
            if (userIds.contains(relation.getUserId())) {
                roleIds.add(relation.getRoleId());
            }
        }
        for (SysUserPost relation : userPostMapper.selectList()) {
            if (userIds.contains(relation.getUserId())) {
                postIds.add(relation.getPostId());
            }
        }
        Map<Long, String> deptNames = new HashMap<>();
        for (SysDept dept : deptMapper.selectList()) {
            if (deptIds.contains(dept.getDeptId()) && !"2".equals(dept.getDelFlag())) {
                deptNames.put(dept.getDeptId(), dept.getDeptName());
            }
        }
        Map<Long, SysRole> roles = new HashMap<>();
        for (SysRole role : roleMapper.selectList()) {
            if (roleIds.contains(role.getRoleId()) && !"2".equals(role.getDelFlag())) {
                roles.put(role.getRoleId(), role);
            }
        }
        Map<Long, SysPost> posts = new HashMap<>();
        for (SysPost post : postMapper.selectList()) {
            if (postIds.contains(post.getPostId()) && !"2".equals(post.getDelFlag())) {
                posts.put(post.getPostId(), post);
            }
        }
        Map<Long, List<Long>> rolesByUser = relationsByUser(userRoleMapper.selectList(), userIds, true);
        Map<Long, List<Long>> postsByUser = relationsByUser(userPostMapper.selectList(), userIds, false);

        List<UserVo> result = new ArrayList<>(users.size());
        for (SysUser user : users) {
            result.add(toVo(user, deptNames, roles, posts,
                    rolesByUser.getOrDefault(user.getUserId(), List.of()),
                    postsByUser.getOrDefault(user.getUserId(), List.of())));
        }
        return result;
    }

    /** 单用户 VO 组装：先收集批量映射再走统一装配。 */
    private UserVo toVo(SysUser user) {
        return toVos(List.of(user)).get(0);
    }

    /** 装配单个用户 VO：基础资料复制 + 展示名与关系 ID/名称列表填充（均无密码）。 */
    private UserVo toVo(SysUser user, Map<Long, String> deptNames, Map<Long, SysRole> roles,
                        Map<Long, SysPost> posts, List<Long> roleIds, List<Long> postIds) {
        UserVo vo = new UserVo();
        vo.setUserId(user.getUserId());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptId() == null ? null : deptNames.get(user.getDeptId()));
        vo.setUserName(user.getUserName());
        vo.setNickName(user.getNickName());
        vo.setEmail(user.getEmail());
        vo.setPhonenumber(user.getPhonenumber());
        vo.setSex(user.getSex());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLoginIp(user.getLoginIp());
        vo.setLoginDate(user.getLoginDate());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleIds(roleIds);
        vo.setPostIds(postIds);
        List<String> roleNames = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            SysRole role = roles.get(roleId);
            roleNames.add(role == null ? null : role.getRoleName());
        }
        vo.setRoleNames(roleNames);
        List<String> postNames = new ArrayList<>(postIds.size());
        for (Long postId : postIds) {
            SysPost post = posts.get(postId);
            postNames.add(post == null ? null : post.getPostName());
        }
        vo.setPostNames(postNames);
        return vo;
    }

    /**
     * 将用户-角色/岗位关系行按用户分组为有序 ID 列表：
     * roles 为 true 时取 roleId，否则取 postId；只保留本页用户的关系。
     */
    private static Map<Long, List<Long>> relationsByUser(
            List<?> relations, Set<Long> userIds, boolean roles) {
        Map<Long, List<Long>> byUser = new HashMap<>();
        for (Object row : relations) {
            Long userId = roles ? ((SysUserRole) row).getUserId() : ((SysUserPost) row).getUserId();
            if (!userIds.contains(userId)) {
                continue;
            }
            Long targetId = roles ? ((SysUserRole) row).getRoleId() : ((SysUserPost) row).getPostId();
            byUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(targetId);
        }
        return byUser;
    }

    /** 将用户实体分页映射为 VO 分页：复用原分页对象，仅替换行记录。 */
    @SuppressWarnings("unchecked")
    private IPage<UserVo> mapPage(IPage<SysUser> page) {
        IPage<UserVo> voPage = (IPage<UserVo>) (IPage<?>) page;
        voPage.setRecords(toVos(page.records()));
        return voPage;
    }
}
