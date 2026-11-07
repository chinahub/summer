package cn.jiebaba.summer.admin.system.dict.dto;

import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.Size;

/** 字典类型新增/修改请求体：修改时 dictId 必填，新增时忽略。 */
public class DictTypeRequest {

    /** 字典主键（修改必填） */
    private Long dictId;

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称长度不能超过100")
    private String dictName;

    /** 字典类型（未删除行内唯一） */
    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型长度不能超过100")
    private String dictType;

    /** 状态（0正常 1停用，缺省 0） */
    private String status;

    public Long getDictId() {
        return dictId;
    }

    public void setDictId(Long dictId) {
        this.dictId = dictId;
    }

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
