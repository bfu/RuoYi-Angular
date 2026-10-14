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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.dsp.cluster.DspReloadNotifier;
import com.ruoyi.dsp.cluster.DspReloadScope;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.domain.DspScriptVersion;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.engine.DspValidationResult;
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

    /** 历史版本 v1 的内容 */
    private static final String V1_CONTENT = "<select resultType=\"map\">select 2</select>";

    /** 运行时数据源ID（启用） */
    private static final Long RUNTIME_DS = 9L;

    private DspScriptMapper dspScriptMapper;

    private DspScriptVersionMapper dspScriptVersionMapper;

    private DspDataSourceMapper dspDataSourceMapper;

    private DspValidator dspValidator;

    private DspEngine dspEngine;

    private DspDataSourceManager dspDataSourceManager;

    private DspReloadNotifier reloadNotifier;

    private DspScriptServiceImpl service;

    @BeforeEach
    void setUp()
    {
        dspScriptMapper = mock(DspScriptMapper.class);
        dspScriptVersionMapper = mock(DspScriptVersionMapper.class);
        dspDataSourceMapper = mock(DspDataSourceMapper.class);
        dspValidator = mock(DspValidator.class);
        dspEngine = mock(DspEngine.class);
        dspDataSourceManager = mock(DspDataSourceManager.class);
        reloadNotifier = mock(DspReloadNotifier.class);
        when(dspValidator.validate(anyLong(), any(), any(), any())).thenReturn(new DspValidationResult());
        service = new DspScriptServiceImpl();
        ReflectionTestUtils.setField(service, "dspScriptMapper", dspScriptMapper);
        ReflectionTestUtils.setField(service, "dspScriptVersionMapper", dspScriptVersionMapper);
        ReflectionTestUtils.setField(service, "dspDataSourceMapper", dspDataSourceMapper);
        ReflectionTestUtils.setField(service, "dspValidator", dspValidator);
        ReflectionTestUtils.setField(service, "dspEngine", dspEngine);
        ReflectionTestUtils.setField(service, "dspDataSourceManager", dspDataSourceManager);
        ReflectionTestUtils.setField(service, "reloadNotifier", reloadNotifier);
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
    void deleteUnloadsStatementsOnlyAfterCommit()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(RUNTIME_DS));

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.deleteDspScriptByIds(new Long[] { SCRIPT_ID });

            // 事务提交前不得卸载运行时语句，也不广播；删除回滚时脚本应保持可用
            verify(dspScriptMapper).deleteDspScriptByIds(any());
            verify(dspEngine, never()).unregister(any(), any());
            verify(reloadNotifier, never()).notify(any(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(dspEngine).unregister(RUNTIME_DS, String.valueOf(SCRIPT_ID));
            verify(reloadNotifier).notify(DspReloadScope.SCRIPT, Set.of(RUNTIME_DS));
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void offlineUnloadsAndBroadcastsOnlyAfterCommit()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(RUNTIME_DS));
        when(dspScriptMapper.updateDspScript(any())).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.offline(SCRIPT_ID, "admin");

            verify(reloadNotifier, never()).notify(any(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(dspEngine).unregister(RUNTIME_DS, String.valueOf(SCRIPT_ID));
            verify(reloadNotifier).notify(DspReloadScope.SCRIPT, List.of(RUNTIME_DS));
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void validateScriptChecksDialectAgainstBoundDataSource()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(RUNTIME_DS));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        service.validateScript(SCRIPT_ID, null);

        // 校验阶段带上绑定数据源的数据库类型，跨方言写法才能被提示出来
        verify(dspValidator).validate(SCRIPT_ID, SLUG, CONTENT, "postgresql");
    }

    @Test
    void validateScriptOnDefaultDataSourceSkipsDialectCheck()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));

        service.validateScript(SCRIPT_ID, CONTENT);

        verify(dspValidator).validate(SCRIPT_ID, SLUG, CONTENT, null);
    }

    @Test
    void validateScriptUsesGivenDataSourceForDialectCheck()
    {
        // 回滚场景：脚本当前绑默认主库，但要校验的历史版本绑定在 RUNTIME_DS 上
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        service.validateScript(SCRIPT_ID, CONTENT, RUNTIME_DS);

        verify(dspValidator).validate(SCRIPT_ID, SLUG, CONTENT, "postgresql");
    }

    @Test
    void validateScriptFallsBackToBoundDataSourceWhenGivenNone()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(RUNTIME_DS));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        service.validateScript(SCRIPT_ID, CONTENT, null);

        verify(dspValidator).validate(SCRIPT_ID, SLUG, CONTENT, "postgresql");
    }

    @Test
    void reloadAllUnloadsRuntimeRegistryBeforeReloading()
    {
        when(dspEngine.loadedDataSourceIds()).thenReturn(new LinkedHashSet<>(List.of(RUNTIME_DS, 0L)));
        when(dspScriptMapper.selectPublishedScriptList()).thenReturn(List.of(publishedScript(RUNTIME_DS)));

        service.reloadAll();

        // 先清空运行时注册，避免库里已删除/已下线的脚本继续可执行
        InOrder order = inOrder(dspEngine);
        order.verify(dspEngine).unregisterAll(RUNTIME_DS);
        order.verify(dspEngine).unregisterAll(0L);
        order.verify(dspEngine).register(RUNTIME_DS, String.valueOf(SCRIPT_ID), SLUG, CONTENT);
    }

    @Test
    void reloadByDataSourceUnloadsStaleStatementsFirst()
    {
        when(dspScriptMapper.selectPublishedScriptListByDataSourceId(RUNTIME_DS))
                .thenReturn(List.of(publishedScript(RUNTIME_DS)));

        service.reloadByDataSource(RUNTIME_DS);

        InOrder order = inOrder(dspEngine);
        order.verify(dspEngine).unregisterAll(RUNTIME_DS);
        order.verify(dspEngine).register(RUNTIME_DS, String.valueOf(SCRIPT_ID), SLUG, CONTENT);
    }

    @Test
    void reloadAllAndBroadcastNotifiesOtherInstances()
    {
        when(dspEngine.loadedDataSourceIds()).thenReturn(Set.of());
        when(dspScriptMapper.selectPublishedScriptList()).thenReturn(List.of());

        service.reloadAllAndBroadcast();

        verify(reloadNotifier).notify(DspReloadScope.ALL, List.of());
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

    @Test
    void publishSnapshotsDataSourceIntoVersion()
    {
        DspScript draft = publishedScript(RUNTIME_DS);
        draft.setStatus("0");
        draft.setCurrentVersion(0);
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(draft);
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        assertEquals(1, service.publish(SCRIPT_ID, "admin"));

        ArgumentCaptor<DspScriptVersion> captor = ArgumentCaptor.forClass(DspScriptVersion.class);
        verify(dspScriptVersionMapper).insertVersion(captor.capture());
        // 版本快照需记录发布时绑定的数据源
        assertEquals(RUNTIME_DS, captor.getValue().getDataSourceId());
        assertEquals(CONTENT, captor.getValue().getContent());
        verify(dspEngine).register(RUNTIME_DS, String.valueOf(SCRIPT_ID), SLUG, CONTENT);
    }

    @Test
    void rebindSyncsCurrentVersionDataSource()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        service.updateDspScript(rebindPayload(RUNTIME_DS));

        // 当前版本快照同步为运行时数据源，保证回滚到当前版本是幂等的
        verify(dspScriptVersionMapper).updateCurrentVersionDataSourceId(SCRIPT_ID, RUNTIME_DS);
    }

    @Test
    void rollbackRestoresContentAndDataSourceOfTargetVersion()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));
        when(dspScriptVersionMapper.selectVersionById(11L)).thenReturn(version(11L, 1, V1_CONTENT, RUNTIME_DS));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        assertEquals(1, service.rollback(SCRIPT_ID, 11L, "admin"));

        ArgumentCaptor<DspScript> captor = ArgumentCaptor.forClass(DspScript.class);
        verify(dspScriptMapper).updateDspScript(captor.capture());
        assertEquals(RUNTIME_DS, captor.getValue().getDataSourceId());
        assertEquals(V1_CONTENT, captor.getValue().getContent());
        verify(dspScriptVersionMapper).markVersionCurrent(11L);
        // 先注册到版本绑定的数据源，再卸载原默认库
        InOrder order = inOrder(dspEngine);
        order.verify(dspEngine).register(RUNTIME_DS, String.valueOf(SCRIPT_ID), SLUG, V1_CONTENT);
        order.verify(dspEngine).unregister(0L, String.valueOf(SCRIPT_ID));
    }

    @Test
    void rollbackWithinSameDataSourceDoesNotUnregister()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(RUNTIME_DS));
        when(dspScriptVersionMapper.selectVersionById(11L)).thenReturn(version(11L, 1, V1_CONTENT, RUNTIME_DS));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "报表库", "0"));

        service.rollback(SCRIPT_ID, 11L, "admin");

        verify(dspEngine).register(RUNTIME_DS, String.valueOf(SCRIPT_ID), SLUG, V1_CONTENT);
        verify(dspEngine, never()).unregister(any(), any());
        verify(dspDataSourceManager, never()).evict(anyLong());
    }

    @Test
    void rollbackToVersionWithDisabledDataSourceIsRejectedBeforeAnyMutation()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));
        when(dspScriptVersionMapper.selectVersionById(11L)).thenReturn(version(11L, 1, V1_CONTENT, RUNTIME_DS));
        when(dspDataSourceMapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(dataSource(RUNTIME_DS, "停用库", "1"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.rollback(SCRIPT_ID, 11L, "admin"));

        assertTrue(exception.getMessage().contains("已停用"));
        verify(dspScriptMapper, never()).updateDspScript(any());
        verify(dspScriptVersionMapper, never()).markVersionCurrent(anyLong());
        verify(dspEngine, never()).register(any(), any(), any(), any());
        verify(dspEngine, never()).unregister(any(), any());
    }

    @Test
    void rollbackToUnknownVersionIsRejected()
    {
        when(dspScriptMapper.selectDspScriptById(SCRIPT_ID)).thenReturn(publishedScript(0L));
        when(dspScriptVersionMapper.selectVersionById(99L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.rollback(SCRIPT_ID, 99L, "admin"));

        verify(dspScriptMapper, never()).updateDspScript(any());
    }

    private DspScriptVersion version(Long versionId, int versionNo, String content, Long dataSourceId)
    {
        DspScriptVersion version = new DspScriptVersion();
        version.setVersionId(versionId);
        version.setScriptId(SCRIPT_ID);
        version.setSlug(SLUG);
        version.setVersion(versionNo);
        version.setContent(content);
        version.setDataSourceId(dataSourceId);
        version.setStatus("0");
        return version;
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
        // 非 MySQL 数据源：用于校验方言提示是否带上正确的数据库类型
        config.setDbType("postgresql");
        return config;
    }
}
