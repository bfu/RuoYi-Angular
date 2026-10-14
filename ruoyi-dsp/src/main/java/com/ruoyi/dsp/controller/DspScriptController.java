package com.ruoyi.dsp.controller;

import java.util.Map;
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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 动态脚本管理 操作处理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/dsp/script")
public class DspScriptController extends BaseController
{
    @Autowired
    private IDspScriptService dspScriptService;

    /**
     * 查询脚本列表
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:list')")
    @GetMapping("/list")
    public TableDataInfo list(DspScript dspScript)
    {
        startPage();
        return getDataTable(dspScriptService.selectDspScriptList(dspScript));
    }

    /**
     * 获取脚本详细信息（含草稿内容）
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:query')")
    @GetMapping("/{scriptId}")
    public AjaxResult getInfo(@PathVariable Long scriptId)
    {
        return success(dspScriptService.selectDspScriptById(scriptId));
    }

    /**
     * 查询脚本版本列表
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:query')")
    @GetMapping("/versions/{scriptId}")
    public AjaxResult versions(@PathVariable Long scriptId)
    {
        return success(dspScriptService.selectVersionList(scriptId));
    }

    /**
     * 获取指定版本详情（含内容快照）
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:query')")
    @GetMapping("/version/{versionId}")
    public AjaxResult versionInfo(@PathVariable Long versionId)
    {
        return success(dspScriptService.selectVersionById(versionId));
    }

    /**
     * 新增脚本
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:add')")
    @Log(title = "动态脚本", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DspScript dspScript)
    {
        return toAjax(dspScriptService.insertDspScript(dspScript));
    }

    /**
     * 修改脚本（保存草稿）
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:edit')")
    @Log(title = "动态脚本", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DspScript dspScript)
    {
        return toAjax(dspScriptService.updateDspScript(dspScript));
    }

    /**
     * 删除脚本
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:remove')")
    @Log(title = "动态脚本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{scriptIds}")
    public AjaxResult remove(@PathVariable Long[] scriptIds)
    {
        return toAjax(dspScriptService.deleteDspScriptByIds(scriptIds));
    }

    /**
     * 校验脚本内容（content 为空时校验库中草稿）
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:validate')")
    @PostMapping("/validate")
    public AjaxResult validate(@RequestBody Map<String, Object> body)
    {
        Long scriptId = toLong(body.get("scriptId"), "scriptId");
        Object content = body.get("content");
        return success(dspScriptService.validateScript(scriptId, content == null ? null : String.valueOf(content)));
    }

    /**
     * 测试脚本语句（事务回滚预览）
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:test')")
    @PostMapping("/test")
    public AjaxResult test(@RequestBody Map<String, Object> body)
    {
        Long scriptId = toLong(body.get("scriptId"), "scriptId");
        String statementId = body.get("statementId") == null ? null : String.valueOf(body.get("statementId"));
        Map<String, Object> params = toParamMap(body.get("params"));
        return success(dspScriptService.testScript(scriptId, statementId, params));
    }

    /**
     * 发布脚本
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:publish')")
    @Log(title = "动态脚本", businessType = BusinessType.UPDATE)
    @PostMapping("/publish")
    public AjaxResult publish(@RequestBody Map<String, Object> body)
    {
        Long scriptId = toLong(body.get("scriptId"), "scriptId");
        return success(dspScriptService.publish(scriptId, getUsername()));
    }

    /**
     * 回滚到指定历史版本
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:rollback')")
    @Log(title = "动态脚本", businessType = BusinessType.UPDATE)
    @PostMapping("/rollback")
    public AjaxResult rollback(@RequestBody Map<String, Object> body)
    {
        Long scriptId = toLong(body.get("scriptId"), "scriptId");
        Long versionId = toLong(body.get("versionId"), "versionId");
        return success(dspScriptService.rollback(scriptId, versionId, getUsername()));
    }

    /**
     * 下线脚本
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:publish')")
    @Log(title = "动态脚本", businessType = BusinessType.UPDATE)
    @PostMapping("/offline")
    public AjaxResult offline(@RequestBody Map<String, Object> body)
    {
        Long scriptId = toLong(body.get("scriptId"), "scriptId");
        return toAjax(dspScriptService.offline(scriptId, getUsername()));
    }

    /**
     * 重新加载全部已发布脚本（多实例部署时会一并通知其余实例重载）
     */
    @PreAuthorize("@ss.hasPermi('dsp:script:publish')")
    @Log(title = "动态脚本", businessType = BusinessType.OTHER)
    @PostMapping("/reload")
    public AjaxResult reload()
    {
        dspScriptService.reloadAllAndBroadcast();
        return success();
    }

    private Long toLong(Object value, String field)
    {
        if (value == null)
        {
            throw new ServiceException(field + "不能为空");
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        try
        {
            return Long.valueOf(String.valueOf(value).trim());
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(field + "格式错误");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toParamMap(Object value)
    {
        if (value instanceof Map)
        {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
