package com.ruoyi.oauth.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.oauth.domain.OauthApiKey;

/**
 * API Key 授权 Mapper 接口
 *
 * @author ruoyi
 */
public interface OauthApiKeyMapper
{
    /**
     * 查询密钥列表（不含摘要，供管理端分页展示）
     */
    List<OauthApiKey> selectOauthApiKeyList(OauthApiKey oauthApiKey);

    /**
     * 根据密钥ID查询
     */
    OauthApiKey selectOauthApiKeyById(Long keyId);

    /**
     * 根据密钥摘要查询（认证入口；含最近使用时间，用于节流更新）
     */
    OauthApiKey selectOauthApiKeyByKeyHash(String keyHash);

    /**
     * 新增密钥
     */
    int insertOauthApiKey(OauthApiKey oauthApiKey);

    /**
     * 修改密钥（动态更新非空字段；expiresTime 为空表示永久有效，会显式清空原过期时间）
     */
    int updateOauthApiKey(OauthApiKey oauthApiKey);

    /**
     * 重置密钥（写入新摘要，旧密钥立即失效）
     */
    int resetOauthApiKey(@Param("keyId") Long keyId, @Param("keyHash") String keyHash, @Param("updateBy") String updateBy);

    /**
     * 批量删除密钥
     */
    int deleteOauthApiKeyByIds(Long[] keyIds);

    /**
     * 更新最近使用时间（认证成功后，best effort）
     */
    int updateLastUsedTime(@Param("keyId") Long keyId, @Param("lastUsedTime") Date lastUsedTime);
}
