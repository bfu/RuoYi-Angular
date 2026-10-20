package com.ruoyi.oauth.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * API Key 授权对象 oauth_api_key
 * <p>
 * 密钥明文只在创建/重置时返回一次，库中仅存 SHA-256 摘要（key_hash），
 * 认证时按摘要精确匹配，因此无法反查明文。
 *
 * @author ruoyi
 */
public class OauthApiKey extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 密钥ID */
    private Long keyId;

    /** 密钥名称（调用方备注用） */
    @Excel(name = "密钥名称")
    private String name;

    /** 密钥明文（仅创建/重置响应返回，不落库） */
    @JsonIgnore
    private String apiKey;

    /** 密钥摘要 SHA-256(apiKey) 十六进制，认证时按此查找 */
    @JsonIgnore
    private String keyHash;

    /** 绑定的用户ID（免登录即代表该用户，权限与该用户登录时一致） */
    private Long userId;

    /** 绑定用户的登录名（关联查询展示，不落库） */
    @Excel(name = "绑定用户")
    private String userName;

    /** 状态(0正常 1停用) */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 过期时间（为空表示永久有效） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresTime;

    /** 最近使用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUsedTime;

    public Long getKeyId()
    {
        return keyId;
    }

    public void setKeyId(Long keyId)
    {
        this.keyId = keyId;
    }

    @NotBlank(message = "密钥名称不能为空")
    @Size(min = 1, max = 128, message = "密钥名称长度不能超过128个字符")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getKeyHash()
    {
        return keyHash;
    }

    public void setKeyHash(String keyHash)
    {
        this.keyHash = keyHash;
    }

    @NotNull(message = "绑定用户不能为空")
    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getExpiresTime()
    {
        return expiresTime;
    }

    public void setExpiresTime(Date expiresTime)
    {
        this.expiresTime = expiresTime;
    }

    public Date getLastUsedTime()
    {
        return lastUsedTime;
    }

    public void setLastUsedTime(Date lastUsedTime)
    {
        this.lastUsedTime = lastUsedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("keyId", getKeyId())
            .append("name", getName())
            .append("userId", getUserId())
            .append("userName", getUserName())
            .append("status", getStatus())
            .append("expiresTime", getExpiresTime())
            .append("lastUsedTime", getLastUsedTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
