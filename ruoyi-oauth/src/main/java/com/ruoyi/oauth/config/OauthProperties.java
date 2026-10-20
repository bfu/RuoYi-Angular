package com.ruoyi.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API Key 免登录授权配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ruoyi.oauth")
public class OauthProperties
{
    /** 是否启用 API Key 免登录；关闭后携带密钥的请求按普通请求走 JWT 流程 */
    private boolean enabled = true;

    /** 携带密钥的请求头名称 */
    private String headerName = "X-API-Key";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getHeaderName()
    {
        return headerName;
    }

    public void setHeaderName(String headerName)
    {
        this.headerName = headerName;
    }
}
