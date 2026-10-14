package com.ruoyi.dsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.dsp.cluster.DspReloadNotifier;
import com.ruoyi.dsp.cluster.DspReloadScope;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;
import com.ruoyi.dsp.mapper.DspScriptMapper;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * {@link DspDataSourceServiceImpl} 单元测试
 * <p>
 * 覆盖：页面回显用的详情接口不回传口令；只有连接配置变更才重建连接池并广播其余实例。
 *
 * @author ruoyi
 */
class DspDataSourceServiceImplTest
{
    private static final Long DATA_SOURCE_ID = 5L;

    private DspDataSourceMapper dspDataSourceMapper;

    private DspEngine dspEngine;

    private DspDataSourceManager dspDataSourceManager;

    private IDspScriptService dspScriptService;

    private DspReloadNotifier reloadNotifier;

    private DspDataSourceServiceImpl service;

    @BeforeEach
    void setUp()
    {
        dspDataSourceMapper = mock(DspDataSourceMapper.class);
        dspEngine = mock(DspEngine.class);
        dspDataSourceManager = mock(DspDataSourceManager.class);
        dspScriptService = mock(IDspScriptService.class);
        reloadNotifier = mock(DspReloadNotifier.class);
        service = new DspDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "dspDataSourceMapper", dspDataSourceMapper);
        ReflectionTestUtils.setField(service, "dspScriptMapper", mock(DspScriptMapper.class));
        ReflectionTestUtils.setField(service, "dspEngine", dspEngine);
        ReflectionTestUtils.setField(service, "dspDataSourceManager", dspDataSourceManager);
        ReflectionTestUtils.setField(service, "dspScriptService", dspScriptService);
        ReflectionTestUtils.setField(service, "reloadNotifier", reloadNotifier);
        // 更新数据源需要登录用户
        SysUser user = new SysUser();
        user.setUserName("admin");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new LoginUser(user, null));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void detailDoesNotReturnPassword()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(DATA_SOURCE_ID)).thenReturn(configured());

        DspDataSource detail = service.selectDspDataSourceDetail(DATA_SOURCE_ID);

        assertNull(detail.getPassword());
        // 其余字段照常返回，供页面回显
        assertEquals("报表库", detail.getName());
        assertEquals("report", detail.getUsername());
        assertEquals("jdbc:mysql://127.0.0.1:3306/report", detail.getUrl());
    }

    @Test
    void detailOfMissingDataSourceReturnsNull()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(404L)).thenReturn(null);

        assertNull(service.selectDspDataSourceDetail(404L));
    }

    @Test
    void runtimeQueryKeepsPasswordForConnectionPool()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(DATA_SOURCE_ID)).thenReturn(configured());

        assertEquals("secret", service.selectDspDataSourceById(DATA_SOURCE_ID).getPassword());
    }

    @Test
    void updateWithConnectionChangeRebuildsPoolAndNotifiesPeers()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(DATA_SOURCE_ID)).thenReturn(configured());
        DspDataSource update = new DspDataSource();
        update.setDataSourceId(DATA_SOURCE_ID);
        update.setName("报表库");
        update.setUrl("jdbc:mysql://127.0.0.1:3307/report");

        service.updateDspDataSource(update);

        verify(dspDataSourceMapper).updateDspDataSource(update);
        // 无事务时「提交后」回调立即执行：卸载语句 → 销毁旧连接池 → 重载 → 广播其余实例
        InOrder order = inOrder(dspEngine, dspDataSourceManager);
        order.verify(dspEngine).unregisterAll(DATA_SOURCE_ID);
        order.verify(dspDataSourceManager).evict(DATA_SOURCE_ID);
        verify(dspScriptService).reloadByDataSource(DATA_SOURCE_ID);
        verify(reloadNotifier).notify(DspReloadScope.DATASOURCE, List.of(DATA_SOURCE_ID));
    }

    @Test
    void updateWithOnlyNameChangeSkipsRuntimeRebuild()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(DATA_SOURCE_ID)).thenReturn(configured());
        DspDataSource update = new DspDataSource();
        update.setDataSourceId(DATA_SOURCE_ID);
        update.setName("报表库-新名字");

        service.updateDspDataSource(update);

        verify(dspDataSourceMapper).updateDspDataSource(update);
        // 仅改名称不重建连接池、不广播，避免在用脚本瞬时不可用
        verify(dspEngine, never()).unregisterAll(anyLong());
        verify(dspDataSourceManager, never()).evict(anyLong());
        verify(reloadNotifier, never()).notify(any(), any());
    }

    private DspDataSource configured()
    {
        DspDataSource source = new DspDataSource();
        source.setDataSourceId(DATA_SOURCE_ID);
        source.setName("报表库");
        source.setStatus("0");
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://127.0.0.1:3306/report");
        source.setUsername("report");
        source.setPassword("secret");
        return source;
    }
}
