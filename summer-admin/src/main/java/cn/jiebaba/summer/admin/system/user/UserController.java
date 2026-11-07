package cn.jiebaba.summer.admin.system.user;

import cn.jiebaba.summer.admin.auth.AdminUserDetails;
import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.admin.system.user.dto.CreateUserRequest;
import cn.jiebaba.summer.admin.system.user.dto.ResetPasswordRequest;
import cn.jiebaba.summer.admin.system.user.dto.UpdateUserRequest;
import cn.jiebaba.summer.admin.system.user.dto.UserVo;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

/**
 * 用户管理接口：数据范围内的分页列表、详情、新增、修改、删除、
 * 状态修改与重置密码。当前用户一律从 {@link AdminUserDetails} 认证主体
 * 解析（绝不信任客户端 userId 之外的范围传参），所有端点声明精确权限标识
 * 由框架强制执行；响应只输出 {@link UserVo}，不含密码等敏感字段。
 */
@RestController
@RequestMapping("/api/system/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 用户分页列表：登录名/手机号模糊与状态过滤，列表受当前用户数据范围约束。 */
    @PreAuthorize(authorities = {"system:user:list"})
    @GetMapping("/list")
    public R<TableData<UserVo>> list(PageQuery pageQuery,
                                     @AuthenticationPrincipal AdminUserDetails currentUser,
                                     @RequestParam(value = "userName", required = false) String userName,
                                     @RequestParam(value = "phonenumber", required = false) String phonenumber,
                                     @RequestParam(value = "status", required = false) String status) {
        IPage<UserVo> page = userService.page(pageQuery, currentUser.getUserId(),
                userName, phonenumber, status);
        return R.ok(TableData.of(page));
    }

    /** 用户详情（数据范围越权时 403）。 */
    @PreAuthorize(authorities = {"system:user:query"})
    @GetMapping("/{userId}")
    public R<UserVo> get(@PathVariable("userId") Long userId,
                         @AuthenticationPrincipal AdminUserDetails currentUser) {
        return R.ok(userService.get(userId, currentUser.getUserId()));
    }

    /** 新增用户（含角色/岗位关系，整体一个事务）。 */
    @PreAuthorize(authorities = {"system:user:add"})
    @PostMapping
    public R<Long> create(@RequestBody CreateUserRequest request,
                          @AuthenticationPrincipal AdminUserDetails currentUser) {
        return R.ok(userService.create(request, currentUser.getUserId()));
    }

    /** 修改用户（非空字段覆盖；roleIds/postIds 为 null 保持原关系）。 */
    @PreAuthorize(authorities = {"system:user:edit"})
    @PutMapping
    public R<Void> update(@RequestBody UpdateUserRequest request,
                          @AuthenticationPrincipal AdminUserDetails currentUser) {
        userService.update(request, currentUser.getUserId());
        return R.ok();
    }

    /** 删除用户（user_id=1 与当前用户自身拒绝，逻辑删除并清理关系）。 */
    @PreAuthorize(authorities = {"system:user:remove"})
    @DeleteMapping("/{userId}")
    public R<Void> delete(@PathVariable("userId") Long userId,
                          @AuthenticationPrincipal AdminUserDetails currentUser) {
        userService.delete(userId, currentUser.getUserId());
        return R.ok();
    }

    /** 修改用户状态（user_id=1 与当前用户自身拒绝）。 */
    @PreAuthorize(authorities = {"system:user:edit"})
    @PutMapping("/{userId}/status")
    public R<Void> updateStatus(@PathVariable("userId") Long userId,
                                @RequestBody StatusBody body,
                                @AuthenticationPrincipal AdminUserDetails currentUser) {
        userService.updateStatus(userId, body == null ? null : body.getStatus(),
                currentUser.getUserId());
        return R.ok();
    }

    /** 管理端重置密码（user_id=1 与当前用户自身拒绝，本人改密走个人中心）。 */
    @PreAuthorize(authorities = {"system:user:resetPwd"})
    @PutMapping("/{userId}/password")
    public R<Void> resetPassword(@PathVariable("userId") Long userId,
                                 @RequestBody ResetPasswordRequest request,
                                 @AuthenticationPrincipal AdminUserDetails currentUser) {
        userService.resetPassword(userId, request == null ? null : request.getPassword(),
                currentUser.getUserId());
        return R.ok();
    }

    /** 状态修改请求体：status 仅允许 0（正常）或 1（停用）。 */
    public static class StatusBody {

        /** 目标状态 */
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
