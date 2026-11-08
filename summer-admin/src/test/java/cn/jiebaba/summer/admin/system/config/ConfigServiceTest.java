package cn.jiebaba.summer.admin.system.config;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.admin.testsupport.WrapperSupport;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.page.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数服务纯内存单测：键名唯一（未删除行内）、空键/空名 400、分页与过滤、
 * 修改覆盖与唯一性排除自身，以及内置初始化标记 sys.init.marker 的全方位保护
 * （创建/改名/修改/删除一律 409，列表与详情不暴露）。内存 Mapper 不回填自增主键，显式指定 ID。
 */
class ConfigServiceTest {

    private ConfigFake mapper;
    private ConfigService configService;
    private long nextId = 100L;

    @BeforeEach
    void setUp() {
        mapper = new ConfigFake();
        configService = new ConfigService(mapper);
        nextId = 100L;
        // 模拟种子：两个普通参数 + 初始化标记（与 sql/summer_admin.sql 一致，config_id=100）
        mapper.insert(config("登录验证码开关", "sys.account.captchaEnabled", "true", 1L));
        mapper.insert(config("账号初始密码", "sys.user.initPassword", "admin123", 2L));
        SysConfig marker = config("初始化完成标记", "sys.init.marker", "1", 100L);
        marker.setConfigType("Y");
        mapper.insert(marker);
    }

    @Test
    void createRequiresNameAndKey() {
        SysConfig noName = config(null, "sys.x", "1", null);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> configService.create(noName)).status());

        SysConfig noKey = config("名称", null, "1", null);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> configService.create(noKey)).status());

        SysConfig blankKey = config("名称", "  ", "1", null);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> configService.create(blankKey)).status());
    }

    @Test
    void createAppliesDefaults() {
        Long id = configService.create(config("用户可注册", "sys.user.register", "true", 10L));

        SysConfig saved = mapper.selectById(id);
        assertEquals("N", saved.getConfigType());
        assertEquals("0", saved.getStatus());
        assertEquals("0", saved.getDelFlag());
    }

    @Test
    void configKeyUniqueAmongNonDeleted() {
        SysConfig duplicate = config("重复键", "sys.user.initPassword", "other", 11L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.create(duplicate)).status());

        // 逻辑删除后同键可复用（种子 3 行 + 复用行，其中临时行已删）
        Long tempId = configService.create(config("临时", "sys.temp.key", "1", 12L));
        configService.delete(tempId);
        configService.create(config("复用", "sys.temp.key", "2", 13L));
        assertEquals(4, mapper.selectList().size());
        assertEquals("0", mapper.selectById(1L).getDelFlag());
    }

    @Test
    void updateAppliesChangesAndUniqueExcludesSelf() {
        SysConfig update = config("验证码改", "sys.account.captchaEnabled", "false", null);
        update.setConfigId(1L);
        configService.update(update);

        SysConfig saved = mapper.selectById(1L);
        assertEquals("验证码改", saved.getConfigName());
        assertEquals("false", saved.getConfigValue());

        SysConfig conflict = config("抢键", "sys.user.initPassword", "x", null);
        conflict.setConfigId(1L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.update(conflict)).status());
    }

    @Test
    void updateNotFoundReturns404() {
        SysConfig missing = config("不存在", "sys.none", "1", null);
        missing.setConfigId(999L);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> configService.update(missing)).status());
    }

    @Test
    void pageFiltersByNameKeyTypeStatusAndExcludesMarker() {
        SysConfig custom = config("皮肤", "sys.index.skinName", "skin-blue", 3L);
        custom.setConfigType("Y");
        mapper.insert(custom);
        SysConfig disabled = config("停用项", "sys.demo.disabled", "0", 4L);
        disabled.setStatus("1");
        mapper.insert(disabled);

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        IPage<SysConfig> page = configService.page(query, null, null, null, null);

        // 5 行中排除 sys.init.marker
        assertEquals(4, page.total());
        assertTrue(page.records().stream()
                .noneMatch(c -> "sys.init.marker".equals(c.getConfigKey())));

        IPage<SysConfig> byName = configService.page(query, "验证码", null, null, null);
        assertEquals(1, byName.total());
        assertEquals("sys.account.captchaEnabled", byName.records().get(0).getConfigKey());

        IPage<SysConfig> byStatus = configService.page(query, null, null, null, "1");
        assertEquals(1, byStatus.total());
        assertEquals("sys.demo.disabled", byStatus.records().get(0).getConfigKey());

        IPage<SysConfig> byType = configService.page(query, null, null, "Y", null);
        assertEquals(1, byType.total(), "初始化标记被排除，只剩皮肤参数");
    }

    @Test
    void pageSlicesByPageNumAndPageSize() {
        PageQuery query = new PageQuery();
        query.setPageNum(2);
        query.setPageSize(1);

        IPage<SysConfig> page = configService.page(query, null, null, null, null);

        assertEquals(2, page.total());
        assertEquals(1, page.records().size());
        assertEquals("sys.user.initPassword", page.records().get(0).getConfigKey());
    }

    @Test
    void getNotFoundReturns404AndMarkerNotExposed() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> configService.get(999L)).status());
        // 初始化标记不作为业务数据暴露给详情接口
        assertEquals(404, assertThrows(BusinessException.class,
                () -> configService.get(100L)).status());
        assertEquals("1", mapper.selectById(100L).getConfigValue(), "标记行必须原样保留");
    }

    @Test
    void markerKeyCannotBeCreated() {
        SysConfig forged = config("伪造标记", "sys.init.marker", "0", 101L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.create(forged)).status());
    }

    @Test
    void markerRowCannotBeUpdated() {
        SysConfig tamper = config("篡改标记名", "sys.init.marker", "0", null);
        tamper.setConfigId(100L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.update(tamper)).status());

        SysConfig valueOnly = config(null, null, "0", null);
        valueOnly.setConfigId(100L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.update(valueOnly)).status());

        SysConfig saved = mapper.selectById(100L);
        assertEquals("1", saved.getConfigValue());
        assertEquals("初始化完成标记", saved.getConfigName());
    }

    @Test
    void markerKeyCannotBeClaimedByRename() {
        SysConfig rename = config("抢标记键", "sys.init.marker", "x", null);
        rename.setConfigId(1L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.update(rename)).status());
        assertEquals("sys.account.captchaEnabled", mapper.selectById(1L).getConfigKey());
    }

    @Test
    void markerRowCannotBeDeleted() {
        assertEquals(409, assertThrows(BusinessException.class,
                () -> configService.delete(100L)).status());

        SysConfig saved = mapper.selectById(100L);
        assertEquals("0", saved.getDelFlag(), "初始化标记禁止删除");
        assertEquals(0, mapper.deletedCountOf(100L), "拒绝删除不得产生逻辑删除记录");
    }

    private SysConfig config(String name, String key, String value, Long id) {
        SysConfig entity = new SysConfig();
        entity.setConfigId(id == null ? nextId++ : id);
        entity.setConfigName(name);
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        entity.setDelFlag("0");
        return entity;
    }

    /** 带逻辑删除语义的 sys_config 内存 Mapper，selectPage 由 WrapperSupport 求值。 */
    private static final class ConfigFake extends InMemoryBaseMapper<SysConfig> implements SysConfigMapper {
        @Override
        protected Long idOf(SysConfig entity) {
            return entity.getConfigId();
        }

        @Override
        public int deleteById(Object id) {
            SysConfig config = rawById(id);
            if (config == null || "2".equals(config.getDelFlag())) {
                return 0;
            }
            config.setDelFlag("2");
            return 1;
        }

        @Override
        public SysConfig selectById(Object id) {
            SysConfig config = rawById(id);
            return config != null && !"2".equals(config.getDelFlag()) ? config : null;
        }

        @Override
        public List<SysConfig> selectList() {
            return rows.stream().filter(c -> !"2".equals(c.getDelFlag())).toList();
        }

        @Override
        public IPage<SysConfig> selectPage(IPage<SysConfig> page, AbstractWrapper<SysConfig, ?> wrapper) {
            List<SysConfig> all = WrapperSupport.select(rows, wrapper, ACCESSOR);
            page.setTotal(all.size());
            int from = (int) Math.min(page.offset(), all.size());
            int to = (int) Math.min(from + page.size(), all.size());
            page.setRecords(all.subList(from, to));
            return page;
        }

        long deletedCountOf(Long id) {
            return rows.stream()
                    .filter(c -> java.util.Objects.equals(c.getConfigId(), id) && "2".equals(c.getDelFlag()))
                    .count();
        }

        private SysConfig rawById(Object id) {
            return rows.stream()
                    .filter(c -> java.util.Objects.equals(c.getConfigId(), id))
                    .findFirst().orElse(null);
        }
    }

    /** 列访问器：同时兼容下划线列名与驼峰属性名。 */
    private static final Function<String, Function<SysConfig, Object>> ACCESSOR = col -> switch (col) {
        case "config_id", "configId" -> SysConfig::getConfigId;
        case "config_name", "configName" -> SysConfig::getConfigName;
        case "config_key", "configKey" -> SysConfig::getConfigKey;
        case "config_value", "configValue" -> SysConfig::getConfigValue;
        case "config_type", "configType" -> SysConfig::getConfigType;
        case "status" -> SysConfig::getStatus;
        case "del_flag", "delFlag" -> SysConfig::getDelFlag;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };
}
