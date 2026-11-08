package cn.jiebaba.summer.admin.system.notice;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLog;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.Set;

/**
 * 公告服务：管理端分页条件查询（可见全部状态，含草稿）、普通已认证用户
 * 只看已发布公告、新增/修改（发布状态切换）与物理删除。列表统一按创建时间
 * 倒序、主键倒序返回，保证排序稳定。sys_notice 无逻辑删除字段。
 */
@Service
public class NoticeService {

    /** 已发布（正常）：对普通已认证用户可见。 */
    public static final String STATUS_PUBLISHED = "0";

    /** 关闭：不再展示。 */
    public static final String STATUS_CLOSED = "1";

    /** 草稿：仅管理端可见。 */
    public static final String STATUS_DRAFT = "2";

    private static final Set<String> VALID_STATUSES =
            Set.of(STATUS_PUBLISHED, STATUS_CLOSED, STATUS_DRAFT);

    private final SysNoticeMapper noticeMapper;

    public NoticeService(SysNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /**
     * 管理端分页查询：标题模糊、类型/状态精确过滤，按创建时间倒序、主键倒序。
     *
     * @param query       分页参数
     * @param noticeTitle 标题模糊词（可空）
     * @param noticeType  公告类型过滤（可空）
     * @param status      状态过滤（可空）
     * @return 分页结果
     */
    public IPage<SysNotice> page(PageQuery query, String noticeTitle, String noticeType, String status) {
        LambdaQueryWrapper<SysNotice> wrapper = baseOrder(new LambdaQueryWrapper<>());
        if (noticeTitle != null && !noticeTitle.isBlank()) {
            wrapper.like(SysNotice::getNoticeTitle, noticeTitle);
        }
        if (noticeType != null && !noticeType.isBlank()) {
            wrapper.eq(SysNotice::getNoticeType, noticeType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysNotice::getStatus, status);
        }
        return noticeMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 普通已认证用户可见的已发布公告分页：仅状态正常，按创建时间倒序、主键倒序。
     *
     * @param query 分页参数
     * @return 已发布公告分页结果
     */
    public IPage<SysNotice> publishedPage(PageQuery query) {
        LambdaQueryWrapper<SysNotice> wrapper = baseOrder(new LambdaQueryWrapper<>());
        wrapper.eq(SysNotice::getStatus, STATUS_PUBLISHED);
        return noticeMapper.selectPage(query.toPage(), wrapper);
    }

    /** 按 ID 查询公告，不存在抛 404。 */
    public SysNotice get(Long noticeId) {
        if (noticeId == null) {
            throw new BusinessException(400, "公告ID不能为空");
        }
        SysNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        return notice;
    }

    /** 新增公告：标题必填，状态缺省为已发布，仅接受合法状态值。 */
    @OperLog(module = "通知公告", action = "新增")
    public Long create(SysNotice notice) {
        validateTitle(notice);
        applyStatus(notice);
        noticeMapper.insert(notice);
        return notice.getNoticeId();
    }

    /** 修改公告：目标必须存在，非空字段覆盖，可切换发布状态。 */
    @OperLog(module = "通知公告", action = "修改")
    public void update(SysNotice notice) {
        if (notice == null || notice.getNoticeId() == null) {
            throw new BusinessException(400, "公告ID不能为空");
        }
        SysNotice existing = get(notice.getNoticeId());
        if (notice.getNoticeTitle() != null) {
            existing.setNoticeTitle(notice.getNoticeTitle());
        }
        if (notice.getNoticeType() != null) {
            existing.setNoticeType(notice.getNoticeType());
        }
        if (notice.getNoticeContent() != null) {
            existing.setNoticeContent(notice.getNoticeContent());
        }
        if (notice.getStatus() != null) {
            existing.setStatus(notice.getStatus());
        }
        validateTitle(existing);
        applyStatus(existing);
        noticeMapper.updateById(existing);
    }

    /** 删除公告（物理删除）。 */
    @OperLog(module = "通知公告", action = "删除")
    public void delete(Long noticeId) {
        get(noticeId);
        noticeMapper.deleteById(noticeId);
    }

    /** 统一列表排序：创建时间倒序，同时间按主键倒序，保证稳定。 */
    private LambdaQueryWrapper<SysNotice> baseOrder(LambdaQueryWrapper<SysNotice> wrapper) {
        wrapper.orderByDesc(SysNotice::getCreateTime);
        wrapper.orderByDesc(SysNotice::getNoticeId);
        return wrapper;
    }

    /** 校验标题非空。 */
    private void validateTitle(SysNotice notice) {
        if (notice == null || notice.getNoticeTitle() == null || notice.getNoticeTitle().isBlank()) {
            throw new BusinessException(400, "公告标题不能为空");
        }
    }

    /** 状态缺省已发布；显式状态必须是合法值，否则 400。 */
    private void applyStatus(SysNotice notice) {
        if (notice.getStatus() == null || notice.getStatus().isBlank()) {
            notice.setStatus(STATUS_PUBLISHED);
            return;
        }
        if (!VALID_STATUSES.contains(notice.getStatus())) {
            throw new BusinessException(400, "公告状态不合法");
        }
    }
}
