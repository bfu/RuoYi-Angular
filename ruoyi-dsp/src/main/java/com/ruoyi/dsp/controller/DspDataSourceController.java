package com.ruoyi.dsp.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.service.IDspDataSourceService;

/**
 * 数据源管理 操作处理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/dsp/datasource")
public class DspDataSourceController extends BaseController
{
    @Autowired
    private IDspDataSourceService dspDataSourceService;

    /**
     * 查询数据源列表
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:list')")
    @GetMapping("/list")
    public TableDataInfo list(DspDataSource dspDataSource)
    {
        startPage();
        return getDataTable(dspDataSourceService.selectDspDataSourceList(dspDataSource));
    }

    /**
     * 查询启用中的数据源（供脚本绑定下拉，不含密码）
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        List<DspDataSource> list = dspDataSourceService.selectEnabledDataSourceList();
        return success(list);
    }

    /**
     * 获取数据源详细信息（不回传数据库口令）
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:query')")
    @GetMapping("/{dataSourceId}")
    public AjaxResult getInfo(@PathVariable Long dataSourceId)
    {
        return success(dspDataSourceService.selectDspDataSourceDetail(dataSourceId));
    }

    /**
     * 新增数据源
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:add')")
    @Log(title = "数据源", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DspDataSource dspDataSource)
    {
        return toAjax(dspDataSourceService.insertDspDataSource(dspDataSource));
    }

    /**
     * 修改数据源
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:edit')")
    @Log(title = "数据源", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DspDataSource dspDataSource)
    {
        return toAjax(dspDataSourceService.updateDspDataSource(dspDataSource));
    }

    /**
     * 删除数据源
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:remove')")
    @Log(title = "数据源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dataSourceIds}")
    public AjaxResult remove(@PathVariable Long[] dataSourceIds)
    {
        return toAjax(dspDataSourceService.deleteDspDataSourceByIds(dataSourceIds));
    }

    /**
     * 测试数据源连接
     */
    @PreAuthorize("@ss.hasPermi('dsp:datasource:test')")
    @Log(title = "数据源", businessType = BusinessType.OTHER)
    @PostMapping("/test")
    public AjaxResult test(@RequestBody DspDataSource dspDataSource)
    {
        return success(dspDataSourceService.testConnection(dspDataSource));
    }
}
