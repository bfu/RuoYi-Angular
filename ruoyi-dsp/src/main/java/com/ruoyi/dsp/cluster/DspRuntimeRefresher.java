package com.ruoyi.dsp.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 运行时重载执行器：按数据库中的已发布内容重建本地语句注册
 * <p>
 * 各范围都按「先卸载、再按库重载」处理，重复执行是幂等的，因此既可用于接收广播，
 * 也可用于本实例的兜底重载。
 *
 * @author ruoyi
 */
@Component
public class DspRuntimeRefresher
{
    private static final Logger log = LoggerFactory.getLogger(DspRuntimeRefresher.class);

    private final DspEngine dspEngine;

    private final DspDataSourceManager dspDataSourceManager;

    private final IDspScriptService dspScriptService;

    public DspRuntimeRefresher(DspEngine dspEngine, DspDataSourceManager dspDataSourceManager,
            IDspScriptService dspScriptService)
    {
        this.dspEngine = dspEngine;
        this.dspDataSourceManager = dspDataSourceManager;
        this.dspScriptService = dspScriptService;
    }

    /**
     * 执行一次重载
     *
     * @param message 广播消息
     */
    public void refresh(DspReloadMessage message)
    {
        switch (message.getScope())
        {
            case ALL:
                dspScriptService.reloadAll();
                break;
            case SCRIPT:
                for (Long dataSourceId : message.getDataSourceIds())
                {
                    dspScriptService.reloadByDataSource(dataSourceId);
                }
                break;
            case DATASOURCE:
                for (Long dataSourceId : message.getDataSourceIds())
                {
                    // 连接配置变了：先销毁旧连接池，避免继续按旧连接串取连接
                    dspDataSourceManager.evict(dataSourceId);
                    dspScriptService.reloadByDataSource(dataSourceId);
                }
                break;
            default:
                log.warn("未知的动态脚本重载范围：{}", message.getScope());
        }
    }
}
