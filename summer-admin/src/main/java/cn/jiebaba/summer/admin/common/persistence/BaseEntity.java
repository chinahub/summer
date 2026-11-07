package cn.jiebaba.summer.admin.common.persistence;

import java.time.LocalDateTime;

/**
 * 核心实体基类：提供审计字段 createBy/createTime/updateBy/updateTime。
 * 字段名默认驼峰转下划线映射（create_by 等），无需显式注解；
 * 关系表与日志表不使用本基类（物理删除、无审计字段）。
 */
public abstract class BaseEntity {

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
