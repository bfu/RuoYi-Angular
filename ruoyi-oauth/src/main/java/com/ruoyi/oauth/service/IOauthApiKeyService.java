package com.ruoyi.oauth.service;

import java.util.List;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.oauth.domain.OauthApiKey;

/**
 * API Key 授权 服务层接口
 *
 * @author ruoyi
 */
public interface IOauthApiKeyService
{
    /**
     * 查询密钥列表
     */
    List<OauthApiKey> selectOauthApiKeyList(OauthApiKey oauthApiKey);

    /**
     * 根据密钥ID查询
     */
    OauthApiKey selectOauthApiKeyById(Long keyId);

    /**
     * 新增密钥，返回明文密钥（仅此一次可见，库中只存摘要）
     */
    String insertOauthApiKey(OauthApiKey oauthApiKey);

    /**
     * 修改密钥（名称/绑定用户/状态/过期时间；expiresTime 为空表示永久有效）
     */
    int updateOauthApiKey(OauthApiKey oauthApiKey);

    /**
     * 重置密钥（换新摘要，旧密钥立即失效），返回新的明文密钥
     */
    String resetOauthApiKey(Long keyId);

    /**
     * 批量删除密钥
     */
    int deleteOauthApiKeyByIds(Long[] keyIds);

    /**
     * API Key 认证（免登录入口）
     * <p>
     * 校验密钥存在、未停用、未过期，且绑定用户可用；通过则构造该用户的身份信息返回。
     *
     * @param apiKey 请求头携带的明文密钥
     * @return 认证成功返回登录用户信息；无效返回 null
     */
    LoginUser authenticate(String apiKey);
}
