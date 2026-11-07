package cn.jiebaba.summer.admin.system.dict;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.admin.common.validation.RequestValidator;
import cn.jiebaba.summer.admin.system.dict.dto.DictDataRequest;
import cn.jiebaba.summer.admin.system.dict.dto.DictDataVo;
import cn.jiebaba.summer.admin.system.dict.dto.DictTypeRequest;
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

import java.util.List;

/**
 * 字典管理接口：类型与数据的分页列表、详情、新增、修改与删除。
 * 维护端点声明 system:dict:* 权限由框架强制执行；唯一对全部已认证用户
 * 开放的是按类型读取启用字典（供前端字典 Hook 使用），响应只输出
 * {@link DictDataVo}，不含删除标志等内部字段。
 */
@RestController
@RequestMapping("/api/system/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /** 字典类型分页列表：名称/类型模糊与状态过滤。 */
    @PreAuthorize(authorities = {"system:dict:query"})
    @GetMapping("/types/list")
    public R<TableData<SysDictType>> typeList(PageQuery pageQuery,
                                              @RequestParam(value = "dictName", required = false) String dictName,
                                              @RequestParam(value = "dictType", required = false) String dictType,
                                              @RequestParam(value = "status", required = false) String status) {
        IPage<SysDictType> page = dictService.page(pageQuery, dictName, dictType, status);
        return R.ok(TableData.of(page));
    }

    /** 字典类型详情。 */
    @PreAuthorize(authorities = {"system:dict:query"})
    @GetMapping("/types/{dictId}")
    public R<SysDictType> typeGet(@PathVariable("dictId") Long dictId) {
        return R.ok(dictService.getType(dictId));
    }

    /** 新增字典类型。 */
    @PreAuthorize(authorities = {"system:dict:add"})
    @PostMapping("/types")
    public R<Long> typeCreate(@RequestBody DictTypeRequest request) {
        RequestValidator.requireValid(request);
        SysDictType type = new SysDictType();
        type.setDictName(request.getDictName());
        type.setDictType(request.getDictType());
        type.setStatus(request.getStatus());
        return R.ok(dictService.createType(type));
    }

    /** 修改字典类型。 */
    @PreAuthorize(authorities = {"system:dict:edit"})
    @PutMapping("/types")
    public R<Void> typeUpdate(@RequestBody DictTypeRequest request) {
        RequestValidator.requireValid(request);
        SysDictType type = new SysDictType();
        type.setDictId(request.getDictId());
        type.setDictName(request.getDictName());
        type.setDictType(request.getDictType());
        type.setStatus(request.getStatus());
        dictService.updateType(type);
        return R.ok();
    }

    /** 删除字典类型（有字典数据时拒绝）。 */
    @PreAuthorize(authorities = {"system:dict:remove"})
    @DeleteMapping("/types/{dictId}")
    public R<Void> typeDelete(@PathVariable("dictId") Long dictId) {
        dictService.deleteType(dictId);
        return R.ok();
    }

    /** 字典数据分页列表：类型精确、标签模糊与状态过滤。 */
    @PreAuthorize(authorities = {"system:dict:query"})
    @GetMapping("/data/list")
    public R<TableData<SysDictData>> dataList(PageQuery pageQuery,
                                              @RequestParam(value = "dictType", required = false) String dictType,
                                              @RequestParam(value = "dictLabel", required = false) String dictLabel,
                                              @RequestParam(value = "status", required = false) String status) {
        IPage<SysDictData> page = dictService.dataPage(pageQuery, dictType, dictLabel, status);
        return R.ok(TableData.of(page));
    }

    /**
     * 按类型读取启用字典数据：仅要求已认证，不限 system:dict:* 权限，
     * 供前端字典 Hook 拉取启用项，返回 {@link DictDataVo} 列表。
     */
    @GetMapping("/data/type/{dictType}")
    public R<List<DictDataVo>> enabledItems(@PathVariable("dictType") String dictType) {
        List<DictDataVo> items = dictService.enabledItems(dictType).stream()
                .map(DictDataVo::of)
                .toList();
        return R.ok(items);
    }

    /** 字典数据详情。 */
    @PreAuthorize(authorities = {"system:dict:query"})
    @GetMapping("/data/{dictCode}")
    public R<SysDictData> dataGet(@PathVariable("dictCode") Long dictCode) {
        return R.ok(dictService.getData(dictCode));
    }

    /** 新增字典数据。 */
    @PreAuthorize(authorities = {"system:dict:add"})
    @PostMapping("/data")
    public R<Long> dataCreate(@RequestBody DictDataRequest request) {
        RequestValidator.requireValid(request);
        return R.ok(dictService.createData(toData(request)));
    }

    /** 修改字典数据。 */
    @PreAuthorize(authorities = {"system:dict:edit"})
    @PutMapping("/data")
    public R<Void> dataUpdate(@RequestBody DictDataRequest request) {
        RequestValidator.requireValid(request);
        dictService.updateData(toData(request));
        return R.ok();
    }

    /** 删除字典数据（逻辑删除）。 */
    @PreAuthorize(authorities = {"system:dict:remove"})
    @DeleteMapping("/data/{dictCode}")
    public R<Void> dataDelete(@PathVariable("dictCode") Long dictCode) {
        dictService.deleteData(dictCode);
        return R.ok();
    }

    /** 将字典数据请求体映射为实体。 */
    private SysDictData toData(DictDataRequest request) {
        SysDictData data = new SysDictData();
        data.setDictCode(request.getDictCode());
        data.setDictType(request.getDictType());
        data.setDictLabel(request.getDictLabel());
        data.setDictValue(request.getDictValue());
        data.setDictSort(request.getDictSort());
        data.setCssClass(request.getCssClass());
        data.setListClass(request.getListClass());
        data.setIsDefault(request.getIsDefault());
        data.setStatus(request.getStatus());
        return data;
    }
}
