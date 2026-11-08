package cn.jiebaba.summer.admin.dashboard;

import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.notice.NoticeService;
import cn.jiebaba.summer.admin.system.notice.SysNotice;
import cn.jiebaba.summer.admin.system.notice.SysNoticeMapper;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;

/**
 * 首页汇总服务：通过 Mapper count 聚合真实数量——未删除用户/角色/部门
 * （Wrapper 查询显式声明 del_flag = '0'，框架不会自动附加逻辑删除条件）
 * 与已发布公告（status = '0'，草稿与关闭不计入）。
 */
@Service
public class DashboardService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysNoticeMapper noticeMapper;

    public DashboardService(SysUserMapper userMapper, SysRoleMapper roleMapper,
            SysDeptMapper deptMapper, SysNoticeMapper noticeMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
        this.noticeMapper = noticeMapper;
    }

    /**
     * 汇总首页四项计数：用户/角色/部门只统计未删除行，公告只统计已发布行。
     * 直接读取数据库实时聚合，无缓存。
     *
     * @return 四项计数
     */
    public DashboardSummary summary() {
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq("del_flag", "0");
        long userCount = userMapper.selectCount(userWrapper);

        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq("del_flag", "0");
        long roleCount = roleMapper.selectCount(roleWrapper);

        LambdaQueryWrapper<SysDept> deptWrapper = new LambdaQueryWrapper<>();
        deptWrapper.eq("del_flag", "0");
        long deptCount = deptMapper.selectCount(deptWrapper);

        LambdaQueryWrapper<SysNotice> noticeWrapper = new LambdaQueryWrapper<>();
        noticeWrapper.eq(SysNotice::getStatus, NoticeService.STATUS_PUBLISHED);
        long noticeCount = noticeMapper.selectCount(noticeWrapper);

        return new DashboardSummary(userCount, roleCount, deptCount, noticeCount);
    }
}
