package cn.jiebaba.summer.admin.system.dict;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 字典服务：类型/数据分页条件查询、新增/修改（类型唯一、同类型值唯一）
 * 与逻辑删除（有数据的类型拒绝删除）；启用项按类型返回启用且排序的数据，
 * 供前端字典 Hook 使用。
 */
@Service
public class DictService {

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    public DictService(SysDictTypeMapper typeMapper, SysDictDataMapper dataMapper) {
        this.typeMapper = typeMapper;
        this.dataMapper = dataMapper;
    }

    /**
     * 分页查询字典类型：名称/类型模糊匹配、状态精确过滤，按主键升序。
     * 注意框架 Wrapper 查询不自动附加逻辑删除条件，需显式声明 del_flag = '0'。
     *
     * @param query    分页参数
     * @param dictName 字典名称模糊词（可空）
     * @param dictType 字典类型模糊词（可空）
     * @param status   状态过滤（可空）
     * @return 分页结果
     */
    public IPage<SysDictType> page(PageQuery query, String dictName, String dictType, String status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        if (dictName != null && !dictName.isBlank()) {
            wrapper.like(SysDictType::getDictName, dictName);
        }
        if (dictType != null && !dictType.isBlank()) {
            wrapper.like(SysDictType::getDictType, dictType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysDictType::getStatus, status);
        }
        wrapper.orderByAsc(SysDictType::getDictId);
        return typeMapper.selectPage(query.toPage(), wrapper);
    }

    /** 按 ID 查询字典类型，不存在抛 404。 */
    public SysDictType getType(Long dictId) {
        if (dictId == null) {
            throw new BusinessException(400, "字典ID不能为空");
        }
        SysDictType type = typeMapper.selectById(dictId);
        if (type == null) {
            throw new BusinessException(404, "字典类型不存在");
        }
        return type;
    }

    /** 新增字典类型：dictType 在未删除类型中唯一，缺省状态正常。 */
    public Long createType(SysDictType type) {
        validateType(type);
        validateTypeUnique(type, null);
        if (type.getStatus() == null || type.getStatus().isBlank()) {
            type.setStatus("0");
        }
        type.setDelFlag("0");
        typeMapper.insert(type);
        return type.getDictId();
    }

    /** 修改字典类型：目标必须存在，非空字段覆盖，dictType 唯一性校验排除自身。 */
    public void updateType(SysDictType type) {
        if (type == null || type.getDictId() == null) {
            throw new BusinessException(400, "字典ID不能为空");
        }
        SysDictType existing = getType(type.getDictId());
        if (type.getDictName() != null) {
            existing.setDictName(type.getDictName());
        }
        if (type.getDictType() != null) {
            existing.setDictType(type.getDictType());
        }
        if (type.getStatus() != null) {
            existing.setStatus(type.getStatus());
        }
        validateType(existing);
        validateTypeUnique(existing, type.getDictId());
        typeMapper.updateById(existing);
    }

    /**
     * 删除字典类型（逻辑删除）：仍存在未删除字典数据时抛 409 拒绝。
     *
     * @param dictId 字典类型 ID
     */
    public void deleteType(Long dictId) {
        SysDictType type = getType(dictId);
        for (SysDictData data : dataMapper.selectList()) {
            if (!"2".equals(data.getDelFlag()) && Objects.equals(type.getDictType(), data.getDictType())) {
                throw new BusinessException(409, "该字典类型下存在字典数据，不允许删除");
            }
        }
        typeMapper.deleteById(dictId);
    }

    /**
     * 分页查询字典数据：类型精确匹配、标签模糊匹配、状态精确过滤，
     * 按排序升序、主键升序。Wrapper 查询显式声明 del_flag = '0'。
     *
     * @param query     分页参数
     * @param dictType  字典类型精确值（可空）
     * @param dictLabel 字典标签模糊词（可空）
     * @param status    状态过滤（可空）
     * @return 分页结果
     */
    public IPage<SysDictData> dataPage(PageQuery query, String dictType, String dictLabel, String status) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        if (dictType != null && !dictType.isBlank()) {
            wrapper.eq(SysDictData::getDictType, dictType);
        }
        if (dictLabel != null && !dictLabel.isBlank()) {
            wrapper.like(SysDictData::getDictLabel, dictLabel);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysDictData::getStatus, status);
        }
        wrapper.orderByAsc(SysDictData::getDictSort);
        wrapper.orderByAsc(SysDictData::getDictCode);
        return dataMapper.selectPage(query.toPage(), wrapper);
    }

    /** 按编码查询字典数据，不存在抛 404。 */
    public SysDictData getData(Long dictCode) {
        if (dictCode == null) {
            throw new BusinessException(400, "字典编码不能为空");
        }
        SysDictData data = dataMapper.selectById(dictCode);
        if (data == null) {
            throw new BusinessException(404, "字典数据不存在");
        }
        return data;
    }

    /**
     * 按类型返回启用字典数据（仅未删除且状态正常的条目），
     * 按排序升序、编码升序排列；类型为空或不存在时返回空列表。
     *
     * @param dictType 字典类型
     * @return 启用的字典数据列表
     */
    public List<SysDictData> enabledItems(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        return dataMapper.selectList().stream()
                .filter(d -> !"2".equals(d.getDelFlag()))
                .filter(d -> "0".equals(d.getStatus()))
                .filter(d -> dictType.equals(d.getDictType()))
                .sorted(Comparator.comparing(SysDictData::getDictSort,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysDictData::getDictCode,
                                Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    /** 新增字典数据：同类型未删除行内 dictValue 唯一，缺省排序 0、状态正常、非默认。 */
    public Long createData(SysDictData data) {
        validateData(data);
        validateDataUnique(data, null);
        if (data.getDictSort() == null) {
            data.setDictSort(0);
        }
        if (data.getStatus() == null || data.getStatus().isBlank()) {
            data.setStatus("0");
        }
        if (data.getIsDefault() == null || data.getIsDefault().isBlank()) {
            data.setIsDefault("N");
        }
        data.setDelFlag("0");
        dataMapper.insert(data);
        return data.getDictCode();
    }

    /** 修改字典数据：目标必须存在，非空字段覆盖，同类型值唯一性校验排除自身。 */
    public void updateData(SysDictData data) {
        if (data == null || data.getDictCode() == null) {
            throw new BusinessException(400, "字典编码不能为空");
        }
        SysDictData existing = getData(data.getDictCode());
        if (data.getDictType() != null) {
            existing.setDictType(data.getDictType());
        }
        if (data.getDictLabel() != null) {
            existing.setDictLabel(data.getDictLabel());
        }
        if (data.getDictValue() != null) {
            existing.setDictValue(data.getDictValue());
        }
        if (data.getDictSort() != null) {
            existing.setDictSort(data.getDictSort());
        }
        if (data.getCssClass() != null) {
            existing.setCssClass(data.getCssClass());
        }
        if (data.getListClass() != null) {
            existing.setListClass(data.getListClass());
        }
        if (data.getIsDefault() != null) {
            existing.setIsDefault(data.getIsDefault());
        }
        if (data.getStatus() != null) {
            existing.setStatus(data.getStatus());
        }
        validateData(existing);
        validateDataUnique(existing, data.getDictCode());
        dataMapper.updateById(existing);
    }

    /** 删除字典数据（逻辑删除，del_flag 0→2）。 */
    public void deleteData(Long dictCode) {
        getData(dictCode);
        dataMapper.deleteById(dictCode);
    }

    /** 校验字典类型名称与类型非空。 */
    private void validateType(SysDictType type) {
        if (type == null || type.getDictName() == null || type.getDictName().isBlank()) {
            throw new BusinessException(400, "字典名称不能为空");
        }
        if (type.getDictType() == null || type.getDictType().isBlank()) {
            throw new BusinessException(400, "字典类型不能为空");
        }
    }

    /** 校验 dictType 在未删除类型中唯一（excludeId 用于更新时排除自身）。 */
    private void validateTypeUnique(SysDictType type, Long excludeId) {
        for (SysDictType other : typeMapper.selectList()) {
            if (Objects.equals(other.getDictId(), excludeId)) {
                continue;
            }
            if (!"2".equals(other.getDelFlag()) && type.getDictType().equals(other.getDictType())) {
                throw new BusinessException(409, "字典类型已存在");
            }
        }
    }

    /** 校验字典数据类型、标签、键值非空。 */
    private void validateData(SysDictData data) {
        if (data == null || data.getDictType() == null || data.getDictType().isBlank()) {
            throw new BusinessException(400, "字典类型不能为空");
        }
        if (data.getDictLabel() == null || data.getDictLabel().isBlank()) {
            throw new BusinessException(400, "字典标签不能为空");
        }
        if (data.getDictValue() == null || data.getDictValue().isBlank()) {
            throw new BusinessException(400, "字典键值不能为空");
        }
    }

    /** 校验同类型未删除行内 dictValue 唯一（excludeId 用于更新时排除自身）。 */
    private void validateDataUnique(SysDictData data, Long excludeId) {
        for (SysDictData other : dataMapper.selectList()) {
            if (Objects.equals(other.getDictCode(), excludeId)) {
                continue;
            }
            if (!"2".equals(other.getDelFlag())
                    && data.getDictType().equals(other.getDictType())
                    && data.getDictValue().equals(other.getDictValue())) {
                throw new BusinessException(409, "同一字典类型下字典键值已存在");
            }
        }
    }
}
