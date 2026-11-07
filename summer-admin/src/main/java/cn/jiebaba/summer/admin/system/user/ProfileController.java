package cn.jiebaba.summer.admin.system.user;

import cn.jiebaba.summer.admin.auth.AdminUserDetails;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.system.user.dto.ChangePasswordRequest;
import cn.jiebaba.summer.admin.system.user.dto.UpdateProfileRequest;
import cn.jiebaba.summer.admin.system.user.dto.UserVo;
import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RestController;

/**
 * 个人中心接口：当前登录用户查看/更新本人资料（白名单字段）与修改密码
 * （必须验证原密码）。端点只要求认证，不声明管理权限标识；
 * 当前用户一律从 {@link AdminUserDetails} 认证主体解析，绝不接受他人 ID。
 */
@RestController
@RequestMapping("/api/system/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    /** 当前用户资料：批量组装的 VO，不含密码等敏感字段。 */
    @GetMapping
    public R<UserVo> profile(@AuthenticationPrincipal AdminUserDetails currentUser) {
        return R.ok(userService.profile(currentUser.getUserId()));
    }

    /** 更新本人资料：仅昵称/邮箱/手机号/性别白名单字段生效。 */
    @PutMapping
    public R<Void> updateProfile(@AuthenticationPrincipal AdminUserDetails currentUser,
                                 @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(currentUser.getUserId(), request);
        return R.ok();
    }

    /** 修改本人密码：验证原密码通过后以新 BCrypt 哈希落库。 */
    @PutMapping("/password")
    public R<Void> changePassword(@AuthenticationPrincipal AdminUserDetails currentUser,
                                  @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.getUserId(), request);
        return R.ok();
    }
}
