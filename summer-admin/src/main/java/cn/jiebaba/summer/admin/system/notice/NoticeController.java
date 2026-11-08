package cn.jiebaba.summer.admin.system.notice;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.admin.common.validation.RequestValidator;
import cn.jiebaba.summer.admin.system.notice.dto.NoticeRequest;
import cn.jiebaba.summer.data.page.IPage;
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
 * 通知公告接口：管理端分页 CRUD（可见草稿），维护端点声明 system:notice:*
 * 权限由框架强制执行；{@code /published/list} 仅要求已认证，只返回已发布公告，
 * 供首页/普通用户查看。
 */
@RestController
@RequestMapping("/api/system/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 管理端公告分页列表：标题模糊与类型/状态过滤，可见全部状态（含草稿）。 */
    @PreAuthorize(authorities = {"system:notice:query"})
    @GetMapping("/list")
    public R<TableData<SysNotice>> list(PageQuery pageQuery,
                                        @RequestParam(value = "noticeTitle", required = false) String noticeTitle,
                                        @RequestParam(value = "noticeType", required = false) String noticeType,
                                        @RequestParam(value = "status", required = false) String status) {
        IPage<SysNotice> page = noticeService.page(pageQuery, noticeTitle, noticeType, status);
        return R.ok(TableData.of(page));
    }

    /**
     * 已发布公告分页列表：仅要求已认证，不限 system:notice:* 权限，
     * 草稿与关闭公告不进入结果，供首页/普通用户查看。
     */
    @GetMapping("/published/list")
    public R<TableData<SysNotice>> publishedList(PageQuery pageQuery) {
        IPage<SysNotice> page = noticeService.publishedPage(pageQuery);
        return R.ok(TableData.of(page));
    }

    /** 公告详情（管理端）。 */
    @PreAuthorize(authorities = {"system:notice:query"})
    @GetMapping("/{noticeId}")
    public R<SysNotice> get(@PathVariable("noticeId") Long noticeId) {
        return R.ok(noticeService.get(noticeId));
    }

    /** 新增公告。 */
    @PreAuthorize(authorities = {"system:notice:add"})
    @PostMapping
    public R<Long> create(@RequestBody NoticeRequest request) {
        RequestValidator.requireValid(request);
        return R.ok(noticeService.create(toNotice(request)));
    }

    /** 修改公告（含发布状态切换）。 */
    @PreAuthorize(authorities = {"system:notice:edit"})
    @PutMapping
    public R<Void> update(@RequestBody NoticeRequest request) {
        RequestValidator.requireValid(request);
        noticeService.update(toNotice(request));
        return R.ok();
    }

    /** 删除公告（物理删除）。 */
    @PreAuthorize(authorities = {"system:notice:remove"})
    @DeleteMapping("/{noticeId}")
    public R<Void> delete(@PathVariable("noticeId") Long noticeId) {
        noticeService.delete(noticeId);
        return R.ok();
    }

    /** 将公告请求体映射为实体。 */
    private SysNotice toNotice(NoticeRequest request) {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(request.getNoticeId());
        notice.setNoticeTitle(request.getNoticeTitle());
        notice.setNoticeType(request.getNoticeType());
        notice.setNoticeContent(request.getNoticeContent());
        notice.setStatus(request.getStatus());
        return notice;
    }
}
