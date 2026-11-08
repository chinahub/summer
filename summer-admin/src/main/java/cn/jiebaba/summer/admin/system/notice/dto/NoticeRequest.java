package cn.jiebaba.summer.admin.system.notice.dto;

import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.Size;

/** 通知公告新增/修改请求体：修改时 noticeId 必填，新增时忽略。 */
public class NoticeRequest {

    /** 公告ID（修改必填） */
    private Long noticeId;

    /** 公告标题 */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 64, message = "公告标题长度不能超过64")
    private String noticeTitle;

    /** 公告类型（1通知 2公告） */
    private String noticeType;

    /** 公告内容 */
    @Size(max = 2000, message = "公告内容长度不能超过2000")
    private String noticeContent;

    /** 状态（0已发布 1关闭 2草稿，缺省 0） */
    private String status;

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public String getNoticeTitle() {
        return noticeTitle;
    }

    public void setNoticeTitle(String noticeTitle) {
        this.noticeTitle = noticeTitle;
    }

    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
        this.noticeType = noticeType;
    }

    public String getNoticeContent() {
        return noticeContent;
    }

    public void setNoticeContent(String noticeContent) {
        this.noticeContent = noticeContent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
