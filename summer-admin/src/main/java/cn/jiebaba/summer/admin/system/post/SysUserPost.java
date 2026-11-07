package cn.jiebaba.summer.admin.system.post;

import cn.jiebaba.summer.data.annotation.TableName;

/** 用户与岗位多对多关系（物理删除，无审计与逻辑删除字段）。 */
@TableName("sys_user_post")
public class SysUserPost {

    /** 用户ID */
    private Long userId;

    /** 岗位ID */
    private Long postId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }
}
