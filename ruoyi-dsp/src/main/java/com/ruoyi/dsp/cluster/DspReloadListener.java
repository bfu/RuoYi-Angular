package com.ruoyi.dsp.cluster;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 动态脚本运行时变更广播接收端
 * <p>
 * 只在收到其它实例的广播时重载：本实例改动时就地生效，无需重复重载。
 *
 * @author ruoyi
 */
@Component
public class DspReloadListener implements MessageListener
{
    private static final Logger log = LoggerFactory.getLogger(DspReloadListener.class);

    private final DspReloadNotifier notifier;

    private final DspRuntimeRefresher refresher;

    public DspReloadListener(DspReloadNotifier notifier, DspRuntimeRefresher refresher)
    {
        this.notifier = notifier;
        this.refresher = refresher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern)
    {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        DspReloadMessage payload = DspReloadMessage.decode(body);
        if (payload == null)
        {
            log.warn("忽略无法解析的动态脚本运行时变更广播：{}", body);
            return;
        }
        if (notifier.isFromSelf(payload))
        {
            return;
        }
        log.info("收到其它实例的动态脚本变更广播，开始重载 scope={}, dataSourceIds={}", payload.getScope(),
                payload.getDataSourceIds());
        try
        {
            refresher.refresh(payload);
        }
        catch (Exception e)
        {
            log.error("按广播重载动态脚本失败 scope={}, dataSourceIds={}", payload.getScope(), payload.getDataSourceIds(), e);
        }
    }
}
