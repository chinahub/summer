package cn.jiebaba.summer.admin.system.config;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.admin.common.validation.RequestValidator;
import cn.jiebaba.summer.admin.system.config.dto.ConfigRequest;
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
 * 参数管理接口：分页列表、详情、新增、修改与删除。维护端点声明
 * system:config:* 权限由框架强制执行；初始化标记 sys.init.marker
 * 由服务层兜底保护（不出现在列表/详情，修改/删除一律 409）。
 */
@RestController
@RequestMapping("/api/system/configs")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /** 参数分页列表：名称/键名模糊与类型/状态过滤。 */
    @PreAuthorize(authorities = {"system:config:query"})
    @GetMapping("/list")
    public R<TableData<SysConfig>> list(PageQuery pageQuery,
                                        @RequestParam(value = "configName", required = false) String configName,
                                        @RequestParam(value = "configKey", required = false) String configKey,
                                        @RequestParam(value = "configType", required = false) String configType,
                                        @RequestParam(value = "status", required = false) String status) {
        IPage<SysConfig> page = configService.page(pageQuery, configName, configKey, configType, status);
        return R.ok(TableData.of(page));
    }

    /** 参数详情。 */
    @PreAuthorize(authorities = {"system:config:query"})
    @GetMapping("/{configId}")
    public R<SysConfig> get(@PathVariable("configId") Long configId) {
        return R.ok(configService.get(configId));
    }

    /** 新增参数（键名唯一）。 */
    @PreAuthorize(authorities = {"system:config:add"})
    @PostMapping
    public R<Long> create(@RequestBody ConfigRequest request) {
        RequestValidator.requireValid(request);
        SysConfig config = new SysConfig();
        config.setConfigName(request.getConfigName());
        config.setConfigKey(request.getConfigKey());
        config.setConfigValue(request.getConfigValue());
        config.setConfigType(request.getConfigType());
        config.setStatus(request.getStatus());
        return R.ok(configService.create(config));
    }

    /** 修改参数。 */
    @PreAuthorize(authorities = {"system:config:edit"})
    @PutMapping
    public R<Void> update(@RequestBody ConfigRequest request) {
        RequestValidator.requireValid(request);
        SysConfig config = new SysConfig();
        config.setConfigId(request.getConfigId());
        config.setConfigName(request.getConfigName());
        config.setConfigKey(request.getConfigKey());
        config.setConfigValue(request.getConfigValue());
        config.setConfigType(request.getConfigType());
        config.setStatus(request.getStatus());
        configService.update(config);
        return R.ok();
    }

    /** 删除参数（逻辑删除）。 */
    @PreAuthorize(authorities = {"system:config:remove"})
    @DeleteMapping("/{configId}")
    public R<Void> delete(@PathVariable("configId") Long configId) {
        configService.delete(configId);
        return R.ok();
    }
}
