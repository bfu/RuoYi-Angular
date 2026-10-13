package com.ruoyi.dsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.engine.DspValidator;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;
import com.ruoyi.dsp.mapper.DspScriptMapper;
import com.ruoyi.dsp.mapper.DspScriptVersionMapper;

/**
 * {@link DspScriptServiceImpl} 数据源绑定相关单元测试
 * <p>
 * 覆盖：换绑数据源的前置校验与「先注册新库、再卸载旧库」顺序；
 * 换绑/执行到停用数据源时的准确报错。
 *
 * @author ruoyi
 */
class DspScriptServiceImplTest
{
    private static final Long SCRIPT_ID = 1L;

    private static final String SLUG = "order_query";

    private static final String CONTENT = "<select resultType=\"map\">select 1</select>";

    /** 运行时数据源ID（启用） */
    private static final Long RUNTIME_DS = 9L;

    private DspScriptMapper dspScriptMapper;

    private DspDataSourceMapper dspDataSourceMapper;

    private DspEngine dspEngine;

    private DspDataSourceManager dspDataSourceManager;

    private DspScriptServiceImpl service;

    @BeforeEach
    void setUp()
    {
        dspScriptMapper = mock(DspScriptMapper.class);
        dspDataSourceMapper = mock(DspDataSourceMapper.class);
        dspEngine = mock(DspEngine.class);
        dspDataSourceManager = mock(DspDataSourceManager.class);
        service = new DspScriptServiceImpl();
        ReflectionTestUtils.setField(service, "dspScriptMapper", dspScriptMapper);
        ReflectionTestUtils.setField(service, "dspScriptVersionMapper", mock(DspScriptVersionMapper.class));
        ReflectionTestUtils.setField(service, "dspDataSourceMapper", dspDataSourceMapper);
        ReflectionTestUtils.setField(service, "dspValidator", mock(DspValidator.class));
        ReflectionTestUtils.setField(service, "dspEngine", dspEngine);
        ReflectionTestUtils.setField(service, "dspDataSourceManager", dspDataSourceManager);
        // 保存脚本需要登录用户
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
    void rebindRegistersNewDataSourceBeforeUnregisteringOld()
    {
        DspScript exist = publishedScript(0L);
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(exist);
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        DspScript update = rebindPayload(RUNTIME_DS);
        service.updateDspScript(update);

        // 先在新数据源注册成功，再卸载旧数据源
        InOrder order = inOrder(dspEngine);
        order.verify(dspEngine).register(RUNTIME_DS, String.valueOf(SCRIPT_ID), SLUG, CONTENT);
        order.verify(dspEngine).unregister(0L, String.valueOf(SCRIPT_ID));
        verify(dspScriptMapper).updateDspScript(update);
        // 旧数据源为默认库，不回收其会话
        verify(dspDataSourceManager, never()).evict(anyLong());
    }

    @Test
    void rebindToDisabledDataSourceIsRejectedBeforeAnyMutation()
    {
        DspScript exist = publishedScript(0L);
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(exist);
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "停用库", "1"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateDspScript(rebindPayload(RUNTIME_DS)));

        assertTrue(exception.getMessage().contains("已停用"));
        // 校验先于任何改动：不写库、不卸载旧语句、不注册新语句
        verify(dspScriptMapper, never()).updateDspScript(any());
        verify(dspEngine, never()).register(any(), any(), any(), any());
        verify(dspEngine, never()).unregister(any(), any());
    }

    @Test
    void rebindToMissingDataSourceIsRejected()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateDspScript(rebindPayload(RUNTIME_DS)));

        assertTrue(exception.getMessage().contains("数据源不存在"));
        verify(dspScriptMapper, never()).updateDspScript(any());
    }

    @Test
    void executeOnDisabledDataSourceReportsDataSourceInsteadOfNotPublished()
    {
        DspScript route = new DspScript();
        route.setScriptId(SCRIPT_ID);
        route.setSlug(SLUG);
        route.setDataSourceId(RUNTIME_DS);
        route.setStatus("1");
        when(dspScriptMapper.selectScriptRouteBySlug(SLUG)).thenReturn(route);
        when(dspEngine.hasNamespace(RUNTIME_DS, String.valueOf(SCRIPT_ID))).thenReturn(false);
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "停用库", "1"));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.execute(SLUG, null));

        assertTrue(exception.getMessage().contains("数据源已停用"), exception.getMessage());
    }

    @Test
    void executeOnOfflineScriptReportsOffline()
    {
        DspScript route = new DspScript();
        route.setScriptId(SCRIPT_ID);
        route.setSlug(SLUG);
        route.setDataSourceId(0L);
        route.setStatus("2");
        when(dspScriptMapper.selectScriptRouteBySlug(SLUG)).thenReturn(route);
        when(dspEngine.hasNamespace(0L, String.valueOf(SCRIPT_ID))).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.execute(SLUG, null));

        assertEquals("脚本已下线：" + SLUG, exception.getMessage());
    }

    @Test
    void draftKeepsBindingToDisabledDataSource()
    {
        // 草稿仅校验数据源存在：已停用的数据源也应能保存草稿
        DspScript exist = publishedScript(0L);
        exist.setStatus("0");
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(exist);
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "停用库", "1"));

        DspScript update = rebindPayload(RUNTIME_DS);
        service.updateDspScript(update);

        // 草稿不触发引擎换绑
        verify(dspEngine, never()).register(any(), any(), any(), any());
        verify(dspEngine, never()).unregister(any(), any());
    }

    private DspScript publishedScript(Long dataSourceId)
    {
        DspScript script = new DspScript();
        script.setScriptId(SCRIPT_ID);
        script.setSlug(SLUG);
        script.setName("订单查询");
        script.setContent(CONTENT);
        script.setDataSourceId(dataSourceId);
        script.setStatus("1");
        script.setCurrentVersion(1);
        return script;
    }

    private DspScript rebindPayload(Long dataSourceId)
    {
        DspScript update = new DspScript();
        update.setScriptId(SCRIPT_ID);
        update.setDataSourceId(dataSourceId);
        return update;
    }

    private DspDataSource dataSource(Long dataSourceId, String name, String status)
    {
        DspDataSource config = new DspDataSource();
        config.setDataSourceId(dataSourceId);
        config.setName(name);
        config.setStatus(status);
        return config;
    }
}
