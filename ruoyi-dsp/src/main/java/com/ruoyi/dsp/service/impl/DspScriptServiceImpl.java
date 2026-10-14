package com.ruoyi.dsp.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.cluster.DspReloadNotifier;
import com.ruoyi.dsp.cluster.DspReloadScope;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.domain.DspScriptVersion;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.engine.DspExecutionResult;
import com.ruoyi.dsp.engine.DspValidationResult;
import com.ruoyi.dsp.engine.DspValidator;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;
import com.ruoyi.dsp.mapper.DspScriptMapper;
import com.ruoyi.dsp.mapper.DspScriptVersionMapper;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 动态脚本定义 服务层实现
 *
 * @author ruoyi
 */
@Service
public class DspScriptServiceImpl implements IDspScriptService
{
    private static final Logger log = LoggerFactory.getLogger(DspScriptServiceImpl.class);

    /** 状态：草稿 */
    private static final String STATUS_DRAFT = "0";

    /** 状态：已发布 */
    private static final String STATUS_PUBLISHED = "1";

    /** 状态：已下线 */
    private static final String STATUS_OFFLINE = "2";

    /** 数据源状态：正常 */
    private static final String DS_STATUS_NORMAL = "0";

    @Autowired
    private DspScriptMapper dspScriptMapper;

    @Autowired
    private DspScriptVersionMapper dspScriptVersionMapper;

    @Autowired
    private DspDataSourceMapper dspDataSourceMapper;

    @Autowired
    private DspValidator dspValidator;

    @Autowired
    private DspEngine dspEngine;

    @Autowired
    private DspDataSourceManager dspDataSourceManager;

    @Autowired
    private DspReloadNotifier reloadNotifier;

    @Override
    public List<DspScript> selectDspScriptList(DspScript dspScript)
    {
        return dspScriptMapper.selectDspScriptList(dspScript);
    }

    @Override
    public DspScript selectDspScriptById(Long scriptId)
    {
        return dspScriptMapper.selectDspScriptById(scriptId);
    }

    @Override
    public boolean checkSlugUnique(DspScript dspScript)
    {
        Long scriptId = dspScript.getScriptId() == null ? -1L : dspScript.getScriptId();
        DspScript exist = dspScriptMapper.selectDspScriptBySlug(dspScript.getSlug());
        return exist == null || exist.getScriptId().equals(scriptId);
    }

    @Override
    public int insertDspScript(DspScript dspScript)
    {
        if (!checkSlugUnique(dspScript))
        {
            throw new ServiceException("新增脚本'" + dspScript.getName() + "'失败，脚本标识(slug)已存在");
        }
        dspScript.setDataSourceId(normalizeDataSourceId(dspScript.getDataSourceId()));
        checkDataSourceExists(dspScript.getDataSourceId());
        if (dspScript.getCurrentVersion() == null)
        {
            dspScript.setCurrentVersion(0);
        }
        if (StringUtils.isBlank(dspScript.getStatus()))
        {
            dspScript.setStatus(STATUS_DRAFT);
        }
        dspScript.setCreateBy(SecurityUtils.getUsername());
        return dspScriptMapper.insertDspScript(dspScript);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDspScript(DspScript dspScript)
    {
        DspScript exist = dspScriptMapper.selectDspScriptById(dspScript.getScriptId());
        if (exist == null)
        {
            throw new ServiceException("脚本不存在");
        }
        if (StringUtils.isNotBlank(dspScript.getSlug()) && !dspScript.getSlug().equals(exist.getSlug()))
        {
            throw new ServiceException("脚本标识(slug)创建后不可修改");
        }
        Long oldDataSourceId = normalizeDataSourceId(exist.getDataSourceId());
        Long newDataSourceId = dspScript.getDataSourceId();
        // 已发布脚本换绑会立即生效，要求目标数据源存在且启用；草稿仅要求存在
        boolean rebind = newDataSourceId != null && !newDataSourceId.equals(oldDataSourceId)
                && STATUS_PUBLISHED.equals(exist.getStatus());
        if (newDataSourceId != null)
        {
            if (rebind)
            {
                checkDataSourceUsable(newDataSourceId);
            }
            else
            {
                checkDataSourceExists(newDataSourceId);
            }
        }
        // slug 不可变，禁止通过更新语句修改
        dspScript.setSlug(null);
        dspScript.setUpdateBy(SecurityUtils.getUsername());
        int rows = dspScriptMapper.updateDspScript(dspScript);
        if (rebind)
        {
            // 先在新数据源注册成功，再卸载旧数据源：注册失败会抛异常回滚事务，旧数据源语句保持可用
            dspEngine.register(newDataSourceId, namespaceOf(exist), exist.getSlug(), exist.getContent());
            dspEngine.unregister(oldDataSourceId, namespaceOf(exist));
            // 当前版本快照同步为运行时数据源，保证「回滚到当前版本」是幂等的
            dspScriptVersionMapper.updateCurrentVersionDataSourceId(exist.getScriptId(), newDataSourceId);
            releaseDataSourceIfIdle(oldDataSourceId);
            notifyPeers(DspReloadScope.SCRIPT, List.of(newDataSourceId, oldDataSourceId));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDspScriptByIds(Long[] scriptIds)
    {
        if (scriptIds == null || scriptIds.length == 0)
        {
            return 0;
        }
        // 先登记待卸载的运行时语句（数据源 → 命名空间），库删成功提交后再动内存
        Map<Long, List<String>> pendingNamespaces = new LinkedHashMap<>();
        for (Long scriptId : scriptIds)
        {
            DspScript exist = dspScriptMapper.selectDspScriptById(scriptId);
            if (exist != null)
            {
                Long dataSourceId = normalizeDataSourceId(exist.getDataSourceId());
                pendingNamespaces.computeIfAbsent(dataSourceId, key -> new ArrayList<>()).add(namespaceOf(exist));
            }
        }
        dspScriptVersionMapper.deleteVersionByScriptIds(scriptIds);
        int rows = dspScriptMapper.deleteDspScriptByIds(scriptIds);
        // 提交后再卸载：删除失败回滚时，内存里的已发布脚本仍保持可用
        DspAfterCommit.run(() ->
        {
            pendingNamespaces.forEach((dataSourceId, namespaces) ->
            {
                namespaces.forEach(namespace -> dspEngine.unregister(dataSourceId, namespace));
                releaseDataSourceIfIdle(dataSourceId);
            });
            // 已在提交后回调里，直接广播：再注册事务同步回调不会被执行
            reloadNotifier.notify(DspReloadScope.SCRIPT, pendingNamespaces.keySet());
        });
        return rows;
    }

    @Override
    public DspValidationResult validateScript(Long scriptId, String content)
    {
        DspScript exist = dspScriptMapper.selectDspScriptById(scriptId);
        if (exist == null)
        {
            throw new ServiceException("脚本不存在");
        }
        String scriptContent = StringUtils.isBlank(content) ? exist.getContent() : content;
        return dspValidator.validate(scriptId, exist.getSlug(), scriptContent);
    }

    @Override
    public DspExecutionResult testScript(Long scriptId, String statementId, Map<String, Object> params)
    {
        DspScript exist = dspScriptMapper.selectDspScriptById(scriptId);
        if (exist == null)
        {
            throw new ServiceException("脚本不存在");
        }
        DspValidationResult validation = dspValidator.validate(scriptId, exist.getSlug(), exist.getContent());
        if (!validation.isValid())
        {
            throw new ServiceException("脚本校验未通过：" + String.join("；", validation.getErrors()));
        }
        // 单语句脚本：语句 id 即脚本 slug，statementId 仅用于兼容显式传参
        String target = StringUtils.isNotBlank(statementId) ? statementId
                : (validation.getStatements().isEmpty() ? null : validation.getStatements().get(0));
        if (StringUtils.isBlank(target) || !validation.getStatements().contains(target))
        {
            throw new ServiceException("语句不存在：" + target);
        }
        // 使用临时命名空间，避免影响已发布版本；在脚本绑定的数据源上执行
        Long dataSourceId = normalizeDataSourceId(exist.getDataSourceId());
        String testNamespace = "__dsp_test_" + scriptId + "_" + System.nanoTime();
        dspEngine.register(dataSourceId, testNamespace, exist.getSlug(), exist.getContent());
        try
        {
            return dspEngine.execute(dataSourceId, testNamespace, target, params, true);
        }
        finally
        {
            dspEngine.unregister(dataSourceId, testNamespace);
        }
    }

    @Override
    public DspExecutionResult execute(String slug, Map<String, Object> params)
    {
        // 对外按 slug 调用，运行时 namespace 取脚本 ID，语句 id 即 slug
        DspScript route = dspScriptMapper.selectScriptRouteBySlug(slug);
        if (route == null)
        {
            throw new ServiceException("脚本不存在：" + slug);
        }
        Long dataSourceId = normalizeDataSourceId(route.getDataSourceId());
        String namespace = String.valueOf(route.getScriptId());
        if (!dspEngine.hasNamespace(dataSourceId, namespace))
        {
            // 未加载时按「数据源不可用 → 已下线 → 未发布」依次给出准确原因
            checkDataSourceUsable(dataSourceId);
            if (STATUS_OFFLINE.equals(route.getStatus()))
            {
                throw new ServiceException("脚本已下线：" + slug);
            }
            throw new ServiceException("脚本未发布或不可用：" + slug);
        }
        return dspEngine.execute(dataSourceId, namespace, slug, params, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long scriptId, String operator)
    {
        DspScript script = dspScriptMapper.selectDspScriptById(scriptId);
        if (script == null)
        {
            throw new ServiceException("脚本不存在");
        }
        if (StringUtils.isBlank(script.getContent()))
        {
            throw new ServiceException("脚本内容为空，无法发布");
        }
        DspValidationResult validation = dspValidator.validate(scriptId, script.getSlug(), script.getContent());
        if (!validation.isValid())
        {
            throw new ServiceException("脚本校验未通过：" + String.join("；", validation.getErrors()));
        }
        Long dataSourceId = normalizeDataSourceId(script.getDataSourceId());
        // 发布前先确认数据源可用，避免校验通过后才发现不可用（此时事务已写入版本）
        checkDataSourceUsable(dataSourceId);
        int newVersion = (script.getCurrentVersion() == null ? 0 : script.getCurrentVersion()) + 1;
        // 历史版本全部置为非当前
        dspScriptVersionMapper.updateVersionStatusByScriptId(scriptId, "0");
        DspScriptVersion version = new DspScriptVersion();
        version.setScriptId(scriptId);
        version.setSlug(script.getSlug());
        version.setVersion(newVersion);
        version.setContent(script.getContent());
        // 版本快照同时记录发布时绑定的数据源，回滚时一并还原
        version.setDataSourceId(dataSourceId);
        version.setStatus(STATUS_PUBLISHED);
        version.setPublishBy(operator);
        version.setPublishTime(DateUtils.getNowDate());
        version.setRemark("发布版本 v" + newVersion);
        dspScriptVersionMapper.insertVersion(version);

        DspScript update = new DspScript();
        update.setScriptId(scriptId);
        update.setCurrentVersion(newVersion);
        update.setStatus(STATUS_PUBLISHED);
        update.setUpdateBy(operator);
        dspScriptMapper.updateDspScript(update);

        // 数据源不存在/停用或脚本非法时抛异常，事务回滚
        dspEngine.register(dataSourceId, namespaceOf(script), script.getSlug(), script.getContent());
        notifyPeers(DspReloadScope.SCRIPT, List.of(dataSourceId));
        return newVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollback(Long scriptId, Long versionId, String operator)
    {
        DspScript script = dspScriptMapper.selectDspScriptById(scriptId);
        if (script == null)
        {
            throw new ServiceException("脚本不存在");
        }
        DspScriptVersion version = dspScriptVersionMapper.selectVersionById(versionId);
        if (version == null || !version.getScriptId().equals(scriptId))
        {
            throw new ServiceException("目标版本不存在或不属于该脚本");
        }
        DspValidationResult validation = dspValidator.validate(scriptId, script.getSlug(), version.getContent());
        if (!validation.isValid())
        {
            throw new ServiceException("目标版本内容校验未通过：" + String.join("；", validation.getErrors()));
        }
        // 回滚即还原该版本发布时的完整状态：内容 + 数据源
        Long currentDataSourceId = normalizeDataSourceId(script.getDataSourceId());
        Long targetDataSourceId = normalizeDataSourceId(version.getDataSourceId());
        checkDataSourceUsable(targetDataSourceId);
        dspScriptVersionMapper.updateVersionStatusByScriptId(scriptId, "0");
        dspScriptVersionMapper.markVersionCurrent(versionId);

        DspScript update = new DspScript();
        update.setScriptId(scriptId);
        update.setContent(version.getContent());
        update.setCurrentVersion(version.getVersion());
        update.setDataSourceId(targetDataSourceId);
        update.setStatus(STATUS_PUBLISHED);
        update.setUpdateBy(operator);
        dspScriptMapper.updateDspScript(update);

        // 先注册到目标版本的数据源（同库时为覆盖注册），事务成功后再卸载原数据源
        dspEngine.register(targetDataSourceId, namespaceOf(script), script.getSlug(), version.getContent());
        if (!targetDataSourceId.equals(currentDataSourceId))
        {
            dspEngine.unregister(currentDataSourceId, namespaceOf(script));
            releaseDataSourceIfIdle(currentDataSourceId);
        }
        // 回滚可能同时换了数据源，新旧两边都要通知
        notifyPeers(DspReloadScope.SCRIPT, List.of(targetDataSourceId, currentDataSourceId));
        return version.getVersion();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int offline(Long scriptId, String operator)
    {
        DspScript script = dspScriptMapper.selectDspScriptById(scriptId);
        if (script == null)
        {
            throw new ServiceException("脚本不存在");
        }
        Long dataSourceId = normalizeDataSourceId(script.getDataSourceId());
        DspScript update = new DspScript();
        update.setScriptId(scriptId);
        update.setStatus(STATUS_OFFLINE);
        update.setUpdateBy(operator);
        int rows = dspScriptMapper.updateDspScript(update);
        // 提交后再卸载：状态更新失败回滚时，内存里的语句保持可用，不会出现「库里已发布、内存没有」
        DspAfterCommit.run(() ->
        {
            dspEngine.unregister(dataSourceId, namespaceOf(script));
            releaseDataSourceIfIdle(dataSourceId);
            // 已在提交后回调里，直接广播：再注册事务同步回调不会被执行
            reloadNotifier.notify(DspReloadScope.SCRIPT, List.of(dataSourceId));
        });
        return rows;
    }

    @Override
    public List<DspScriptVersion> selectVersionList(Long scriptId)
    {
        return dspScriptVersionMapper.selectVersionListByScriptId(scriptId);
    }

    @Override
    public DspScriptVersion selectVersionById(Long versionId)
    {
        return dspScriptVersionMapper.selectVersionById(versionId);
    }

    @Override
    public void reloadAll()
    {
        // 先清空运行时注册再按库重建：数据库里已删除/已下线的脚本不能继续可执行
        for (Long dataSourceId : dspEngine.loadedDataSourceIds())
        {
            dspEngine.unregisterAll(dataSourceId);
        }
        List<DspScript> published = dspScriptMapper.selectPublishedScriptList();
        int success = 0;
        for (DspScript script : published)
        {
            try
            {
                dspEngine.register(script.getDataSourceId(), namespaceOf(script), script.getSlug(), script.getContent());
                success++;
            }
            catch (ServiceException e)
            {
                // 数据源不存在/停用等业务原因：跳过该脚本
                log.warn("跳过动态脚本加载 slug={}：{}", script.getSlug(), e.getMessage());
            }
            catch (Exception e)
            {
                log.error("加载动态脚本失败 slug={}", script.getSlug(), e);
            }
        }
        log.info("动态脚本重载完成，已加载 {}/{} 个已发布脚本", success, published.size());
    }

    @Override
    public void reloadAllAndBroadcast()
    {
        reloadAll();
        // 手动整体重载顺带通知其余实例，省去逐实例调用
        reloadNotifier.notify(DspReloadScope.ALL, List.of());
    }

    @Override
    public void reloadByDataSource(Long dataSourceId)
    {
        // 先卸载该数据源下的现有注册，避免已删除/已下线的脚本残留在内存
        dspEngine.unregisterAll(dataSourceId);
        List<DspScript> published = dspScriptMapper.selectPublishedScriptListByDataSourceId(dataSourceId);
        int success = 0;
        for (DspScript script : published)
        {
            try
            {
                dspEngine.register(dataSourceId, namespaceOf(script), script.getSlug(), script.getContent());
                success++;
            }
            catch (Exception e)
            {
                log.error("重载动态脚本失败 dataSourceId={}, slug={}", dataSourceId, script.getSlug(), e);
            }
        }
        log.info("数据源 {} 脚本重载完成，已加载 {}/{}", dataSourceId, success, published.size());
    }

    /**
     * 通知其余实例重载运行时注册
     * <p>
     * 只在事务提交后广播：否则其余实例会读到变更前的库内容而保持旧状态。已经处于提交后回调里的场景
     * 要直接调用 {@code reloadNotifier.notify}，此时再注册事务同步回调不会被执行。
     *
     * @param scope         重载范围
     * @param dataSourceIds 受影响的数据源ID
     */
    private void notifyPeers(DspReloadScope scope, Collection<Long> dataSourceIds)
    {
        DspAfterCommit.run(() -> reloadNotifier.notify(scope, dataSourceIds));
    }

    /**
     * 数据源ID 归一化：null 视为默认主库
     */
    private Long normalizeDataSourceId(Long dataSourceId)
    {
        return dataSourceId == null ? DspDataSource.DEFAULT_ID : dataSourceId;
    }

    /**
     * 校验绑定的数据源存在（草稿保存等尚未生效的场景，不校验启用状态）
     */
    private void checkDataSourceExists(Long dataSourceId)
    {
        if (DspDataSourceManager.isDefault(dataSourceId))
        {
            return;
        }
        if (dspDataSourceMapper.selectDspDataSourceById(dataSourceId) == null)
        {
            throw new ServiceException("数据源不存在：" + dataSourceId);
        }
    }

    /**
     * 校验绑定的数据源存在且启用（发布、换绑、执行等会立即生效的场景）
     */
    private void checkDataSourceUsable(Long dataSourceId)
    {
        if (DspDataSourceManager.isDefault(dataSourceId))
        {
            return;
        }
        DspDataSource config = dspDataSourceMapper.selectDspDataSourceById(dataSourceId);
        if (config == null)
        {
            throw new ServiceException("脚本绑定的数据源不存在：" + dataSourceId);
        }
        if (!DS_STATUS_NORMAL.equals(config.getStatus()))
        {
            throw new ServiceException("脚本绑定的数据源已停用：" + config.getName());
        }
    }

    /**
     * 数据源上已无可执行语句时回收其运行时连接池（默认库不回收，下次使用时按需重建）
     */
    private void releaseDataSourceIfIdle(Long dataSourceId)
    {
        if (DspDataSourceManager.isDefault(dataSourceId) || dspEngine.hasAnyNamespace(dataSourceId))
        {
            return;
        }
        dspDataSourceManager.evict(dataSourceId);
    }

    /**
     * 运行时 namespace：取脚本 ID（语句 id 取脚本 slug，由平台拼接为完整语句 id）
     */
    private String namespaceOf(DspScript script)
    {
        return script == null || script.getScriptId() == null ? "" : String.valueOf(script.getScriptId());
    }
}
