package com.ruoyi.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.excel.utils.ExcelUtil;
import com.ruoyi.system.domain.bo.SysDictDataBo;
import com.ruoyi.system.domain.vo.SysDictDataVo;
import com.ruoyi.system.service.SysDictDataService;
import com.ruoyi.system.service.SysDictTypeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据字典信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/dict/data")
public class SysDictDataController extends BaseController {

    private final SysDictDataService dictDataService;
    private final SysDictTypeService dictTypeService;

    /**
     * 查询字典数据列表
     */
    @SaCheckPermission("system:dict:list")
    @GetMapping("/list")
    public TableDataInfo<SysDictDataVo> list(SysDictDataBo dictData, PageQuery pageQuery) {
        return dictDataService.selectPageDictDataList(dictData, pageQuery);
    }

    /**
     * 导出字典数据列表
     */
    @Log(title = "字典数据", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:dict:export")
    @PostMapping("/export")
    public void export(SysDictDataBo dictData, HttpServletResponse response) {
        List<SysDictDataVo> list = dictDataService.selectDictDataList(dictData);
        ExcelUtil.exportExcel(list, "字典数据", SysDictDataVo.class, response);
    }

    /**
     * 查询字典数据详细
     *
     * @param dictCode 字典code
     */
    @SaCheckPermission("system:dict:query")
    @GetMapping(value = "/{dictCode}")
    public R<SysDictDataVo> getInfo(@PathVariable Long dictCode) {
        return R.ok(dictDataService.selectDictDataById(dictCode));
    }

    /**
     * 根据字典类型查询字典数据信息
     *
     * @param dictType 字典类型
     */
    @GetMapping(value = "/type/{dictType}")
    public R<List<SysDictDataVo>> dictType(@PathVariable String dictType) {
        List<SysDictDataVo> data = dictTypeService.selectDictDataByType(dictType);
        if (ObjectUtil.isNull(data)) {
            data = new ArrayList<>();
        }
        return R.ok(data);
    }

    /**
     * 新增字典类型
     */
    @SaCheckPermission("system:dict:add")
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysDictDataBo dict) {
        if (!dictDataService.checkDictDataUnique(dict)) {
            return R.fail("新增字典数据'" + dict.getDictValue() + "'失败，字典键值已存在");
        }
        dictDataService.insertDictData(dict);
        return R.ok();
    }

    /**
     * 修改保存字典类型
     */
    @SaCheckPermission("system:dict:edit")
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysDictDataBo dict) {
        if (!dictDataService.checkDictDataUnique(dict)) {
            return R.fail("修改字典数据'" + dict.getDictValue() + "'失败，字典键值已存在");
        }
        dictDataService.updateDictData(dict);
        return R.ok();
    }

    /**
     * 删除字典类型
     *
     * @param dictCodes 字典code串
     */
    @SaCheckPermission("system:dict:remove")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictCodes}")
    public R<Void> remove(@PathVariable Long[] dictCodes) {
        dictDataService.deleteDictDataByIds(dictCodes);
        return R.ok();
    }
}
