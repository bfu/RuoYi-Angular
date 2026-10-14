package com.ruoyi.dsp.cluster;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 动态脚本集群广播配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "dsp.cluster")
public class DspClusterProperties
{
    /** 是否开启运行时变更广播；单实例部署可关闭，关闭后退化为「只在本实例生效」 */
    private boolean enabled = true;

    /** 广播频道；同一 Redis 上的多个应用需分别配置，避免相互触发重载 */
    private String reloadChannel = "ruoyi:dsp:reload";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getReloadChannel()
    {
        return reloadChannel;
    }

    public void setReloadChannel(String reloadChannel)
    {
        this.reloadChannel = reloadChannel;
    }
}
