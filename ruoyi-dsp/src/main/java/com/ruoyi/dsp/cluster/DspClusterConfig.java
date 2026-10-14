package com.ruoyi.dsp.cluster;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 动态脚本集群广播装配
 *
 * @author ruoyi
 */
@Configuration
public class DspClusterConfig
{
    /**
     * 订阅运行时变更广播频道；{@code dsp.cluster.enabled=false} 时不订阅（单实例部署）
     */
    @Bean
    @ConditionalOnProperty(prefix = "dsp.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer dspReloadListenerContainer(RedisConnectionFactory connectionFactory,
            DspReloadListener dspReloadListener, DspClusterProperties properties)
    {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(dspReloadListener, new ChannelTopic(properties.getReloadChannel()));
        return container;
    }
}
