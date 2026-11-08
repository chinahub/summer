package cn.jiebaba.summer.admin.system.config;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLog;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.Objects;

/**
 * 参数服务：分页条件查询、新增/修改（键名唯一）与逻辑删除，全部直接读取数据库，
 * 不引入缓存刷新。内置初始化标记 {@code sys.init.marker} 是只读配置：
 * 不出现在普通参数列表/详情中，任何创建、改名、修改、删除尝试一律 409。
 */
@Service
public class ConfigService {

    /** 初始化完成标记键：SQL 种子幂等机制依赖，禁止业务侧触碰。 */
    public static final String INIT_MARKER_KEY = "sys.init.marker";

    private final SysConfigMapper configMapper;

    public ConfigService(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 分页查询参数：名称/键名模糊、类型/状态精确过滤，按主键升序。
     * 注意框架 Wrapper 查询不自动附加逻辑删除条件，需显式声明 del_flag = '0'；
     * 初始化标记不作为业务数据返回。
     *
     * @param query      分页参数
     * @param configName 参数名称模糊词（可空）
     * @param configKey  参数键名模糊词（可空）
     * @param configType 系统内置过滤（可空）
     * @param status     状态过滤（可空）
     * @return 分页结果
     */
    public IPage<SysConfig> page(PageQuery query, String configName, String configKey,
            String configType, String status) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        wrapper.ne("config_key", INIT_MARKER_KEY);
        if (configName != null && !configName.isBlank()) {
            wrapper.like(SysConfig::getConfigName, configName);
        }
        if (configKey != null && !configKey.isBlank()) {
            wrapper.like(SysConfig::getConfigKey, configKey);
        }
        if (configType != null && !configType.isBlank()) {
            wrapper.eq(SysConfig::getConfigType, configType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysConfig::getStatus, status);
        }
        wrapper.orderByAsc(SysConfig::getConfigId);
        return configMapper.selectPage(query.toPage(), wrapper);
    }

    /** 按 ID 查询参数，不存在或已逻辑删除抛 404；初始化标记不对外暴露。 */
    public SysConfig get(Long configId) {
        SysConfig config = requireExisting(configId);
        if (INIT_MARKER_KEY.equals(config.getConfigKey())) {
            throw new BusinessException(404, "参数不存在");
        }
        return config;
    }

    /** 新增参数：键名非空且在未删除行内唯一；初始化标记键一律拒绝。 */
    @OperLog(module = "参数管理", action = "新增")
    public Long create(SysConfig config) {
        validateRequired(config);
        rejectMarkerKey(config.getConfigKey());
        validateKeyUnique(config, null);
        if (config.getConfigType() == null || config.getConfigType().isBlank()) {
            config.setConfigType("N");
        }
        if (config.getStatus() == null || config.getStatus().isBlank()) {
            config.setStatus("0");
        }
        config.setDelFlag("0");
        configMapper.insert(config);
        return config.getConfigId();
    }

    /**
     * 修改参数：目标必须存在，非空字段覆盖，键名唯一性校验排除自身。
     * 已存在的初始化标记行不可修改，普通参数也不可改名为初始化标记键。
     */
    @OperLog(module = "参数管理", action = "修改")
    public void update(SysConfig config) {
        if (config == null || config.getConfigId() == null) {
            throw new BusinessException(400, "参数ID不能为空");
        }
        SysConfig existing = requireExisting(config.getConfigId());
        if (INIT_MARKER_KEY.equals(existing.getConfigKey())) {
            throw new BusinessException(409, "初始化标记为内置配置，不允许修改");
        }
        if (config.getConfigName() != null) {
            existing.setConfigName(config.getConfigName());
        }
        if (config.getConfigKey() != null && !config.getConfigKey().equals(existing.getConfigKey())) {
            rejectMarkerKey(config.getConfigKey());
            existing.setConfigKey(config.getConfigKey());
        }
        if (config.getConfigValue() != null) {
            existing.setConfigValue(config.getConfigValue());
        }
        if (config.getConfigType() != null) {
            existing.setConfigType(config.getConfigType());
        }
        if (config.getStatus() != null) {
            existing.setStatus(config.getStatus());
        }
        validateRequired(existing);
        validateKeyUnique(existing, config.getConfigId());
        configMapper.updateById(existing);
    }

    /** 删除参数（逻辑删除，del_flag 0→2）；初始化标记禁止删除。 */
    @OperLog(module = "参数管理", action = "删除")
    public void delete(Long configId) {
        SysConfig existing = requireExisting(configId);
        if (INIT_MARKER_KEY.equals(existing.getConfigKey())) {
            throw new BusinessException(409, "初始化标记为内置配置，不允许删除");
        }
        configMapper.deleteById(configId);
    }

    /** 取未删除实体，不存在抛 404（不区分标记，供内部守卫使用）。 */
    private SysConfig requireExisting(Long configId) {
        if (configId == null) {
            throw new BusinessException(400, "参数ID不能为空");
        }
        SysConfig config = configMapper.selectById(configId);
        if (config == null || "2".equals(config.getDelFlag())) {
            throw new BusinessException(404, "参数不存在");
        }
        return config;
    }

    /** 拒绝初始化标记键：创建与改名共用。 */
    private void rejectMarkerKey(String configKey) {
        if (INIT_MARKER_KEY.equals(configKey)) {
            throw new BusinessException(409, "初始化标记为内置配置，不允许操作");
        }
    }

    /** 校验参数名称与键名非空。 */
    private void validateRequired(SysConfig config) {
        if (config == null || config.getConfigName() == null || config.getConfigName().isBlank()) {
            throw new BusinessException(400, "参数名称不能为空");
        }
        if (config.getConfigKey() == null || config.getConfigKey().isBlank()) {
            throw new BusinessException(400, "参数键名不能为空");
        }
    }

    /** 校验键名在未删除行内唯一（excludeId 用于更新时排除自身）。 */
    private void validateKeyUnique(SysConfig config, Long excludeId) {
        for (SysConfig other : configMapper.selectList()) {
            if (Objects.equals(other.getConfigId(), excludeId)) {
                continue;
            }
            if (!"2".equals(other.getDelFlag()) && config.getConfigKey().equals(other.getConfigKey())) {
                throw new BusinessException(409, "参数键名已存在");
            }
        }
    }
}
