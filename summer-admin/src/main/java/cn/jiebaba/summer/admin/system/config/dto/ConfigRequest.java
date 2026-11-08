package cn.jiebaba.summer.admin.system.config.dto;

import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.Size;

/** 系统参数新增/修改请求体：修改时 configId 必填，新增时忽略。 */
public class ConfigRequest {

    /** 参数主键（修改必填） */
    private Long configId;

    /** 参数名称 */
    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称长度不能超过100")
    private String configName;

    /** 参数键名（未删除行内唯一，sys.init.marker 为内置只读键） */
    @NotBlank(message = "参数键名不能为空")
    @Size(max = 100, message = "参数键名长度不能超过100")
    private String configKey;

    /** 参数键值 */
    @Size(max = 500, message = "参数键值长度不能超过500")
    private String configValue;

    /** 系统内置（Y是 N否，缺省 N） */
    private String configType;

    /** 状态（0正常 1停用，缺省 0） */
    private String status;

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
