package cn.jiebaba.summer.admin.system.dict.dto;

import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.Size;

/** 字典数据新增/修改请求体：修改时 dictCode 必填，新增时忽略。 */
public class DictDataRequest {

    /** 字典编码（修改必填） */
    private Long dictCode;

    /** 字典类型 */
    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型长度不能超过100")
    private String dictType;

    /** 字典标签 */
    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签长度不能超过100")
    private String dictLabel;

    /** 字典键值（同类型未删除行内唯一） */
    @NotBlank(message = "字典键值不能为空")
    @Size(max = 100, message = "字典键值长度不能超过100")
    private String dictValue;

    /** 字典排序（缺省 0） */
    private Integer dictSort;

    /** 样式属性 */
    @Size(max = 100, message = "样式属性长度不能超过100")
    private String cssClass;

    /** 回显样式 */
    @Size(max = 100, message = "回显样式长度不能超过100")
    private String listClass;

    /** 是否默认（Y是 N否，缺省 N） */
    private String isDefault;

    /** 状态（0正常 1停用，缺省 0） */
    private String status;

    public Long getDictCode() {
        return dictCode;
    }

    public void setDictCode(Long dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getDictLabel() {
        return dictLabel;
    }

    public void setDictLabel(String dictLabel) {
        this.dictLabel = dictLabel;
    }

    public String getDictValue() {
        return dictValue;
    }

    public void setDictValue(String dictValue) {
        this.dictValue = dictValue;
    }

    public Integer getDictSort() {
        return dictSort;
    }

    public void setDictSort(Integer dictSort) {
        this.dictSort = dictSort;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public String getListClass() {
        return listClass;
    }

    public void setListClass(String listClass) {
        this.listClass = listClass;
    }

    public String getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(String isDefault) {
        this.isDefault = isDefault;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
