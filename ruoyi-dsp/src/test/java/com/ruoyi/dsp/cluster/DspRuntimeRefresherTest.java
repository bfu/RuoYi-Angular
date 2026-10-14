package com.ruoyi.dsp.cluster;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * {@link DspRuntimeRefresher} 单元测试
 *
 * @author ruoyi
 */
class DspRuntimeRefresherTest
{
    private DspEngine dspEngine;

    private DspDataSourceManager dspDataSourceManager;

    private IDspScriptService dspScriptService;

    private DspRuntimeRefresher refresher;

    @BeforeEach
    void setUp()
    {
        dspEngine = mock(DspEngine.class);
        dspDataSourceManager = mock(DspDataSourceManager.class);
        dspScriptService = mock(IDspScriptService.class);
        refresher = new DspRuntimeRefresher(dspEngine, dspDataSourceManager, dspScriptService);
    }

    @Test
    void scriptScopeReloadsEachAffectedDataSource()
    {
        refresher.refresh(new DspReloadMessage("other", DspReloadScope.SCRIPT, List.of(9L, 5L)));

        verify(dspScriptService).reloadByDataSource(9L);
        verify(dspScriptService).reloadByDataSource(5L);
        // 脚本变更不重建连接池
        verify(dspDataSourceManager, never()).evict(anyLong());
    }

    @Test
    void dataSourceScopeEvictsOldPoolBeforeReloading()
    {
        refresher.refresh(new DspReloadMessage("other", DspReloadScope.DATASOURCE, List.of(5L)));

        InOrder order = inOrder(dspDataSourceManager, dspScriptService);
        order.verify(dspDataSourceManager).evict(5L);
        order.verify(dspScriptService).reloadByDataSource(5L);
    }

    @Test
    void allScopeReloadsEverything()
    {
        refresher.refresh(new DspReloadMessage("other", DspReloadScope.ALL, List.of()));

        verify(dspScriptService).reloadAll();
        verify(dspScriptService, never()).reloadByDataSource(anyLong());
    }
}
