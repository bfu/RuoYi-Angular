package com.ruoyi.dsp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.dsp.generator.DspGenerateRequest;
import com.ruoyi.dsp.generator.DspMetaQuery;
import com.ruoyi.dsp.service.IDspGeneratorService;

/**
 * 脚本生成 操作处理
 * <p>
 * 交互链路：选择数据源 → 选择实例（库/模式）→ 选择数据表 → 按表结构生成 CRUD 脚本。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/dsp/generator")
public class DspGeneratorController extends BaseController
{
    @Autowired
    private IDspGeneratorService dspGeneratorService;

    /**
     * 可选数据源下拉（启用中，不含密码）
     */
    @PreAuthorize("@ss.hasPermi('dsp:generator:query')")
    @GetMapping("/datasources")
    public AjaxResult datasources()
    {
        return success(dspGeneratorService.selectDataSourceOptions());
    }

    /**
     * 数据源下的实例列表（MySQL 库名 / PostgreSQL、Oracle 模式）
     */
    @PreAuthorize("@ss.hasPermi('dsp:generator:query')")
    @GetMapping("/instances")
    public AjaxResult instances(Long dataSourceId)
    {
        return success(dspGeneratorService.listInstances(dataSourceId));
    }

    /**
     * 实例下的数据表列表（tableName 支持模糊搜索）
     */
    @PreAuthorize("@ss.hasPermi('dsp:generator:query')")
    @GetMapping("/tables")
    public AjaxResult tables(DspMetaQuery query)
    {
        return success(dspGeneratorService.listTables(query));
    }

    /**
     * 数据表字段结构
     */
    @PreAuthorize("@ss.hasPermi('dsp:generator:query')")
    @GetMapping("/columns")
    public AjaxResult columns(DspMetaQuery query)
    {
        return success(dspGeneratorService.listColumns(query));
    }

    /**
     * 生成预览：根据表结构生成 6 条 CRUD 脚本（查询所有/翻页查询/批量添加/单条添加/修改/删除），不落库
     */
    @PreAuthorize("@ss.hasPermi('dsp:generator:preview')")
    @PostMapping("/preview")
    public AjaxResult preview(@RequestBody DspMetaQuery query)
    {
        return success(dspGeneratorService.preview(query));
    }

    /**
     * 批量生成脚本草稿
     */
    @PreAuthorize("@ss.hasPermi('dsp:generator:generate')")
    @Log(title = "脚本生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody DspGenerateRequest request)
    {
        return success(dspGeneratorService.generate(request));
    }
}
