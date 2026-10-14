package com.ruoyi.dsp.cluster;

import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 动态脚本运行时变更广播发送端
 * <p>
 * 广播失败不影响本实例（本实例的内存变更已经完成），但其余实例会保持旧状态，
 * 可调用 {@code POST /dsp/script/reload} 或重启修正，因此这里只记录错误日志。
 *
 * @author ruoyi
 */
@Component
public class DspReloadNotifier
{
    private static final Logger log = LoggerFactory.getLogger(DspReloadNotifier.class);

    /** 本实例标识：用于忽略自己发出的广播，避免重复重载 */
    private final String instanceId = UUID.randomUUID().toString();

    private final StringRedisTemplate redisTemplate;

    private final DspClusterProperties properties;

    public DspReloadNotifier(StringRedisTemplate redisTemplate, DspClusterProperties properties)
    {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 广播运行时变更
     *
     * @param scope         重载范围
     * @param dataSourceIds 受影响的数据源ID（{@code ALL} 时可为空）
     */
    public void notify(DspReloadScope scope, Collection<Long> dataSourceIds)
    {
        if (!properties.isEnabled())
        {
            return;
        }
        DspReloadMessage message = new DspReloadMessage(instanceId, scope, dataSourceIds);
        try
        {
            redisTemplate.convertAndSend(properties.getReloadChannel(), message.encode());
            log.info("已广播动态脚本运行时变更 instanceId={}, scope={}, dataSourceIds={}", instanceId, scope,
                    message.getDataSourceIds());
        }
        catch (Exception e)
        {
            log.error("广播动态脚本运行时变更失败 scope={}, dataSourceIds={}，其余实例可能保持旧状态", scope, dataSourceIds, e);
        }
    }

    /**
     * 是否为本实例发出的广播
     */
    public boolean isFromSelf(DspReloadMessage message)
    {
        return instanceId.equals(message.getInstanceId());
    }
}
