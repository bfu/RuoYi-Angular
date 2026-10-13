package com.ruoyi.dsp.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 动态脚本统一执行 接口
 * <p>
 * 业务调用方无需开发 Controller/Service/Mapper，约定 slug 与 statementId 即可执行已发布脚本。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/dsp/exec")
public class DspExecuteController extends BaseController
{
    @Autowired
    private IDspScriptService dspScriptService;

    /**
     * 统一执行入口（POST，JSON 参数）
     *
     * @param slug        脚本标识
     * @param statementId 语句 id
     * @param params      参数（可为空）
     */
    @PreAuthorize("@ss.hasPermi('dsp:execute')")
    @PostMapping("/{slug}/{statementId}")
    public AjaxResult execute(@PathVariable String slug, @PathVariable String statementId,
            @RequestBody(required = false) Map<String, Object> params)
    {
        return success(dspScriptService.execute(slug, statementId, params));
    }

    /**
     * 统一执行入口（GET，仅建议用于只读 select）
     */
    @PreAuthorize("@ss.hasPermi('dsp:execute')")
    @GetMapping("/{slug}/{statementId}")
    public AjaxResult executeGet(@PathVariable String slug, @PathVariable String statementId,
            @RequestParam(required = false) Map<String, Object> params)
    {
        return success(dspScriptService.execute(slug, statementId, params));
    }
}
