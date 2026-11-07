package cn.jiebaba.summer.admin.system.dict;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典服务纯内存单测：类型唯一（未删除行内）、同类型字典值唯一、
 * 启用项只返回启用且按排序升序、有数据的类型拒绝删除、逻辑删除语义
 * （del_flag 0→2）与默认值补齐。内存 Mapper 不回填自增主键，显式指定 ID。
 */
class DictServiceTest {

    private DictTypeFake typeMapper;
    private DictDataFake dataMapper;
    private DictService dictService;
    private long nextId = 100L;

    @BeforeEach
    void setUp() {
        typeMapper = new DictTypeFake();
        dataMapper = new DictDataFake();
        dictService = new DictService(typeMapper, dataMapper);
        nextId = 100L;
    }

    @Test
    void createTypeRequiresNameAndType() {
        SysDictType noName = type("系统状态", null);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> dictService.createType(noName)).status());

        SysDictType noType = type(null, "sys_x");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> dictService.createType(noType)).status());
    }

    @Test
    void createTypeAppliesDefaults() {
        Long id = dictService.createType(type("系统状态", "sys_normal_disable"));

        SysDictType saved = typeMapper.selectById(id);
        assertEquals("0", saved.getStatus());
        assertEquals("0", saved.getDelFlag());
    }

    @Test
    void dictTypeUniqueAmongNonDeleted() {
        Long first = dictService.createType(type("颜色", "sys_color"));

        SysDictType duplicate = type("颜色二", "sys_color");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> dictService.createType(duplicate)).status());

        // 逻辑删除后同类型可复用
        SysDictType deleted = type("将被删除", "sys_temp");
        Long deletedId = dictService.createType(deleted);
        dictService.deleteType(deletedId);
        dictService.createType(type("复用", "sys_temp"));
        assertEquals(2, typeMapper.selectList().size());
        assertEquals("0", typeMapper.selectById(first).getDelFlag());
    }

    @Test
    void updateTypeUniqueExcludesSelf() {
        Long id = dictService.createType(type("颜色", "sys_color"));
        dictService.createType(type("性别", "sys_sex"));

        SysDictType conflict = type("改名冲突", "sys_sex");
        conflict.setDictId(id);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> dictService.updateType(conflict)).status());

        SysDictType self = type("颜色改", "sys_color");
        self.setDictId(id);
        dictService.updateType(self);
        assertEquals("颜色改", typeMapper.selectById(id).getDictName());
    }

    @Test
    void updateTypeNotFoundReturns404() {
        SysDictType missing = type("不存在", "sys_none");
        missing.setDictId(999L);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> dictService.updateType(missing)).status());
    }

    @Test
    void getTypeNotFoundReturns404() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> dictService.getType(999L)).status());
    }

    @Test
    void deleteTypeRefusesWhenDataExists() {
        Long typeId = dictService.createType(type("颜色", "sys_color"));
        dictService.createData(data("sys_color", "红", "0", 1));

        assertEquals(409, assertThrows(BusinessException.class,
                () -> dictService.deleteType(typeId)).status());
        assertEquals("0", typeMapper.selectById(typeId).getDelFlag());
    }

    @Test
    void deleteTypeLogicalDeletesWithoutData() {
        Long typeId = dictService.createType(type("颜色", "sys_color"));

        dictService.deleteType(typeId);

        assertNull(typeMapper.selectById(typeId));
        assertEquals(1, typeMapper.deletedCountOf(typeId));
    }

    @Test
    void enabledItemsReturnsEnabledSortedOnly() {
        dictService.createType(type("颜色", "sys_color"));
        dictService.createData(data("sys_color", "蓝", "2", 2));
        dictService.createData(data("sys_color", "红", "1", 1));
        SysDictData disabled = data("sys_color", "灰", "0", 0);
        disabled.setStatus("1");
        dictService.createData(disabled);
        SysDictData deleted = data("sys_color", "黑", "9", 9);
        Long deletedCode = dictService.createData(deleted);
        dictService.deleteData(deletedCode);

        List<SysDictData> items = dictService.enabledItems("sys_color");

        assertEquals(2, items.size());
        assertEquals("红", items.get(0).getDictLabel());
        assertEquals("蓝", items.get(1).getDictLabel());
        assertTrue(items.stream().allMatch(d -> "0".equals(d.getStatus())));
        assertTrue(items.stream().allMatch(d -> "0".equals(d.getDelFlag())));
    }

    @Test
    void enabledItemsEmptyForUnknownOrBlankType() {
        assertTrue(dictService.enabledItems("sys_unknown").isEmpty());
        assertTrue(dictService.enabledItems("").isEmpty());
    }

    @Test
    void createDataRequiresFields() {
        SysDictData noType = data(null, "红", "1", 1);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> dictService.createData(noType)).status());

        SysDictData noLabel = data("sys_color", null, "1", 1);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> dictService.createData(noLabel)).status());

        SysDictData noValue = data("sys_color", "红", null, 1);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> dictService.createData(noValue)).status());
    }

    @Test
    void createDataAppliesDefaults() {
        Long code = dictService.createData(data("sys_color", "红", "1", null));

        SysDictData saved = dataMapper.selectById(code);
        assertEquals(Integer.valueOf(0), saved.getDictSort());
        assertEquals("0", saved.getStatus());
        assertEquals("N", saved.getIsDefault());
        assertEquals("0", saved.getDelFlag());
    }

    @Test
    void dictValueUniqueWithinSameTypeAmongNonDeleted() {
        dictService.createData(data("sys_color", "红", "1", 1));

        SysDictData duplicate = data("sys_color", "红色", "1", 2);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> dictService.createData(duplicate)).status());

        // 同值不同类型合法；逻辑删除后同类型同值可复用
        dictService.createData(data("sys_size", "红", "1", 1));
        Long code = dictService.createData(data("sys_color", "临时", "9", 9));
        dictService.deleteData(code);
        dictService.createData(data("sys_color", "复用", "9", 9));
        assertEquals(3, dataMapper.selectList().size());
    }

    @Test
    void updateDataUniqueExcludesSelf() {
        Long first = dictService.createData(data("sys_color", "红", "1", 1));
        dictService.createData(data("sys_color", "蓝", "2", 2));

        SysDictData conflict = data("sys_color", "红改", "2", 3);
        conflict.setDictCode(first);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> dictService.updateData(conflict)).status());

        SysDictData self = data("sys_color", "深红", "1", 5);
        self.setDictCode(first);
        dictService.updateData(self);
        assertEquals("深红", dataMapper.selectById(first).getDictLabel());
        assertEquals(Integer.valueOf(5), dataMapper.selectById(first).getDictSort());
    }

    @Test
    void getDataNotFoundReturns404() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> dictService.getData(999L)).status());
    }

    private SysDictType type(String name, String type) {
        SysDictType entity = new SysDictType();
        entity.setDictId(nextId++);
        entity.setDictName(name);
        entity.setDictType(type);
        return entity;
    }

    private SysDictData data(String type, String label, String value, Integer sort) {
        SysDictData entity = new SysDictData();
        entity.setDictCode(nextId++);
        entity.setDictType(type);
        entity.setDictLabel(label);
        entity.setDictValue(value);
        entity.setDictSort(sort);
        return entity;
    }

    /** 带逻辑删除语义的 sys_dict_type 内存 Mapper。 */
    private static final class DictTypeFake extends InMemoryBaseMapper<SysDictType> implements SysDictTypeMapper {
        @Override
        protected Long idOf(SysDictType entity) {
            return entity.getDictId();
        }

        @Override
        public int deleteById(Object id) {
            SysDictType type = selectById(id);
            if (type == null) {
                return 0;
            }
            type.setDelFlag("2");
            return 1;
        }

        @Override
        public SysDictType selectById(Object id) {
            return rows.stream()
                    .filter(t -> java.util.Objects.equals(t.getDictId(), id) && !"2".equals(t.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<SysDictType> selectList() {
            return rows.stream().filter(t -> !"2".equals(t.getDelFlag())).toList();
        }

        long deletedCountOf(Long id) {
            return rows.stream()
                    .filter(t -> java.util.Objects.equals(t.getDictId(), id) && "2".equals(t.getDelFlag()))
                    .count();
        }
    }

    /** 带逻辑删除语义的 sys_dict_data 内存 Mapper。 */
    private static final class DictDataFake extends InMemoryBaseMapper<SysDictData> implements SysDictDataMapper {
        @Override
        protected Long idOf(SysDictData entity) {
            return entity.getDictCode();
        }

        @Override
        public int deleteById(Object id) {
            SysDictData data = selectById(id);
            if (data == null) {
                return 0;
            }
            data.setDelFlag("2");
            return 1;
        }

        @Override
        public SysDictData selectById(Object id) {
            return rows.stream()
                    .filter(d -> java.util.Objects.equals(d.getDictCode(), id) && !"2".equals(d.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<SysDictData> selectList() {
            return rows.stream().filter(d -> !"2".equals(d.getDelFlag())).toList();
        }
    }
}
