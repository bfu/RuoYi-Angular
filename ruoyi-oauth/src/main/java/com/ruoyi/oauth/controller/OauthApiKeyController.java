package com.ruoyi.oauth.controller;

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
import com.ruoyi.oauth.domain.OauthApiKey;
import com.ruoyi.oauth.service.IOauthApiKeyService;

/**
 * API Key 授权 操作处理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/oauth/apikey")
public class OauthApiKeyController extends BaseController
{
    @Autowired
    private IOauthApiKeyService oauthApiKeyService;

    /**
     * 查询密钥列表
     */
    @PreAuthorize("@ss.hasPermi('oauth:apikey:list')")
    @GetMapping("/list")
    public TableDataInfo list(OauthApiKey oauthApiKey)
    {
        startPage();
        return getDataTable(oauthApiKeyService.selectOauthApiKeyList(oauthApiKey));
    }

    /**
     * 获取密钥详细信息
     */
    @PreAuthorize("@ss.hasPermi('oauth:apikey:query')")
    @GetMapping("/{keyId}")
    public AjaxResult getInfo(@PathVariable Long keyId)
    {
        return success(oauthApiKeyService.selectOauthApiKeyById(keyId));
    }

    /**
     * 新增密钥（明文密钥仅此一次返回，请妥善保存）
     */
    @PreAuthorize("@ss.hasPermi('oauth:apikey:add')")
    @Log(title = "API Key 授权", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OauthApiKey oauthApiKey)
    {
        String apiKey = oauthApiKeyService.insertOauthApiKey(oauthApiKey);
        return success().put("apiKey", apiKey);
    }

    /**
     * 修改密钥（名称/绑定用户/状态/过期时间；过期时间为空表示永久有效）
     */
    @PreAuthorize("@ss.hasPermi('oauth:apikey:edit')")
    @Log(title = "API Key 授权", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OauthApiKey oauthApiKey)
    {
        return toAjax(oauthApiKeyService.updateOauthApiKey(oauthApiKey));
    }

    /**
     * 重置密钥（换新密钥，旧密钥立即失效；明文仅此一次返回）
     */
    @PreAuthorize("@ss.hasPermi('oauth:apikey:reset')")
    @Log(title = "API Key 授权", businessType = BusinessType.UPDATE)
    @PostMapping("/reset/{keyId}")
    public AjaxResult reset(@PathVariable Long keyId)
    {
        String apiKey = oauthApiKeyService.resetOauthApiKey(keyId);
        return success().put("apiKey", apiKey);
    }

    /**
     * 删除密钥
     */
    @PreAuthorize("@ss.hasPermi('oauth:apikey:remove')")
    @Log(title = "API Key 授权", businessType = BusinessType.DELETE)
    @DeleteMapping("/{keyIds}")
    public AjaxResult remove(@PathVariable Long[] keyIds)
    {
        return toAjax(oauthApiKeyService.deleteOauthApiKeyByIds(keyIds));
    }
}
