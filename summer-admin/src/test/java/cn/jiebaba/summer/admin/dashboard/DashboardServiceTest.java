package cn.jiebaba.summer.admin.dashboard;

import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.notice.SysNotice;
import cn.jiebaba.summer.admin.system.notice.SysNoticeMapper;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.admin.testsupport.WrapperSupport;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 首页汇总纯内存单测：固定 Mapper 数据断言用户/角色/部门/已发布公告四项计数；
 * 逻辑删除（del_flag=2）的用户、角色、部门不计入，草稿与关闭公告不计入。
 * 计数经 Wrapper 查询（显式 del_flag=0）由 WrapperSupport 求值。
 */
class DashboardServiceTest {

    private UserFake userMapper;
    private RoleFake roleMapper;
    private DeptFake deptMapper;
    private NoticeFake noticeMapper;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        userMapper = new UserFake();
        roleMapper = new RoleFake();
        deptMapper = new DeptFake();
        noticeMapper = new NoticeFake();
        dashboardService = new DashboardService(userMapper, roleMapper, deptMapper, noticeMapper);
    }

    @Test
    void summaryCountsNonDeletedEntitiesAndPublishedNoticesOnly() {
        userMapper.insert(user(1L, "0"));
        userMapper.insert(user(2L, "0"));
        userMapper.insert(user(3L, "2"));
        roleMapper.insert(role(1L, "0"));
        roleMapper.insert(role(2L, "2"));
        deptMapper.insert(dept(1L, "0"));
        deptMapper.insert(dept(2L, "0"));
        deptMapper.insert(dept(3L, "2"));
        noticeMapper.insert(notice(1L, "0"));
        noticeMapper.insert(notice(2L, "0"));
        noticeMapper.insert(notice(3L, "1"));
        noticeMapper.insert(notice(4L, "2"));

        DashboardSummary summary = dashboardService.summary();

        assertEquals(2, summary.getUserCount());
        assertEquals(1, summary.getRoleCount());
        assertEquals(2, summary.getDeptCount());
        assertEquals(2, summary.getNoticeCount(), "仅已发布公告计入");
    }

    @Test
    void summaryEmptyWhenNoData() {
        DashboardSummary summary = dashboardService.summary();

        assertEquals(0, summary.getUserCount());
        assertEquals(0, summary.getRoleCount());
        assertEquals(0, summary.getDeptCount());
        assertEquals(0, summary.getNoticeCount());
    }

    private SysUser user(Long id, String delFlag) {
        SysUser entity = new SysUser();
        entity.setUserId(id);
        entity.setDelFlag(delFlag);
        return entity;
    }

    private SysRole role(Long id, String delFlag) {
        SysRole entity = new SysRole();
        entity.setRoleId(id);
        entity.setDelFlag(delFlag);
        return entity;
    }

    private SysDept dept(Long id, String delFlag) {
        SysDept entity = new SysDept();
        entity.setDeptId(id);
        entity.setDelFlag(delFlag);
        return entity;
    }

    private SysNotice notice(Long id, String status) {
        SysNotice entity = new SysNotice();
        entity.setNoticeId(id);
        entity.setStatus(status);
        return entity;
    }

    /** 只读计数内存 Mapper：selectCount 由 WrapperSupport 求值。 */
    private abstract static class CountingFake<T> extends InMemoryBaseMapper<T> {
        private final Function<String, Function<T, Object>> accessor;

        CountingFake(Function<String, Function<T, Object>> accessor) {
            this.accessor = accessor;
        }

        @Override
        public long selectCount(AbstractWrapper<T, ?> wrapper) {
            return WrapperSupport.select(rows, wrapper, accessor).size();
        }
    }

    private static final class UserFake extends CountingFake<SysUser> implements SysUserMapper {
        UserFake() {
            super(USER_ACCESSOR);
        }

        @Override
        protected Long idOf(SysUser entity) {
            return entity.getUserId();
        }
    }

    private static final class RoleFake extends CountingFake<SysRole> implements SysRoleMapper {
        RoleFake() {
            super(ROLE_ACCESSOR);
        }

        @Override
        protected Long idOf(SysRole entity) {
            return entity.getRoleId();
        }
    }

    private static final class DeptFake extends CountingFake<SysDept> implements SysDeptMapper {
        DeptFake() {
            super(DEPT_ACCESSOR);
        }

        @Override
        protected Long idOf(SysDept entity) {
            return entity.getDeptId();
        }
    }

    private static final class NoticeFake extends CountingFake<SysNotice> implements SysNoticeMapper {
        NoticeFake() {
            super(NOTICE_ACCESSOR);
        }

        @Override
        protected Long idOf(SysNotice entity) {
            return entity.getNoticeId();
        }
    }

    private static final Function<String, Function<SysUser, Object>> USER_ACCESSOR = col -> switch (col) {
        case "user_id", "userId" -> SysUser::getUserId;
        case "del_flag", "delFlag" -> SysUser::getDelFlag;
        case "status" -> SysUser::getStatus;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };

    private static final Function<String, Function<SysRole, Object>> ROLE_ACCESSOR = col -> switch (col) {
        case "role_id", "roleId" -> SysRole::getRoleId;
        case "del_flag", "delFlag" -> SysRole::getDelFlag;
        case "status" -> SysRole::getStatus;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };

    private static final Function<String, Function<SysDept, Object>> DEPT_ACCESSOR = col -> switch (col) {
        case "dept_id", "deptId" -> SysDept::getDeptId;
        case "del_flag", "delFlag" -> SysDept::getDelFlag;
        case "status" -> SysDept::getStatus;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };

    private static final Function<String, Function<SysNotice, Object>> NOTICE_ACCESSOR = col -> switch (col) {
        case "notice_id", "noticeId" -> SysNotice::getNoticeId;
        case "status" -> SysNotice::getStatus;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };
}
