package com.ruoyi.dsp.service.impl;

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
        if (dspScript.getDataSourceId() != null)
        {
            checkDataSourceExists(dspScript.getDataSourceId());
        }
        // slug 不可变，禁止通过更新语句修改
        dspScript.setSlug(null);
        dspScript.setUpdateBy(SecurityUtils.getUsername());
        int rows = dspScriptMapper.updateDspScript(dspScript);
        // 已发布脚本换绑数据源：立即热重绑定到新数据源，避免引擎与环境不一致
        Long newDataSourceId = dspScript.getDataSourceId();
        if (newDataSourceId != null && !newDataSourceId.equals(normalizeDataSourceId(exist.getDataSourceId()))
                && STATUS_PUBLISHED.equals(exist.getStatus()))
        {
            dspEngine.unregister(exist.getDataSourceId(), namespaceOf(exist));
            dspEngine.register(newDataSourceId, namespaceOf(exist), exist.getSlug(), exist.getContent());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDspScriptByIds(Long[] scriptIds)
    {
        for (Long scriptId : scriptIds)
        {
            DspScript exist = dspScriptMapper.selectDspScriptById(scriptId);
            if (exist != null)
            {
                dspEngine.unregister(exist.getDataSourceId(), namespaceOf(exist));
            }
        }
        dspScriptVersionMapper.deleteVersionByScriptIds(scriptIds);
        return dspScriptMapper.deleteDspScriptByIds(scriptIds);
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
        int newVersion = (script.getCurrentVersion() == null ? 0 : script.getCurrentVersion()) + 1;
        // 历史版本全部置为非当前
        dspScriptVersionMapper.updateVersionStatusByScriptId(scriptId, "0");
        DspScriptVersion version = new DspScriptVersion();
        version.setScriptId(scriptId);
        version.setSlug(script.getSlug());
        version.setVersion(newVersion);
        version.setContent(script.getContent());
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
        Long dataSourceId = normalizeDataSourceId(script.getDataSourceId());
        dspScriptVersionMapper.updateVersionStatusByScriptId(scriptId, "0");
        dspScriptVersionMapper.markVersionCurrent(versionId);

        DspScript update = new DspScript();
        update.setScriptId(scriptId);
        update.setContent(version.getContent());
        update.setCurrentVersion(version.getVersion());
        update.setStatus(STATUS_PUBLISHED);
        update.setUpdateBy(operator);
        dspScriptMapper.updateDspScript(update);

        dspEngine.register(dataSourceId, namespaceOf(script), script.getSlug(), version.getContent());
        return version.getVersion();
    }

    @Override
    public int offline(Long scriptId, String operator)
    {
        DspScript script = dspScriptMapper.selectDspScriptById(scriptId);
        if (script == null)
        {
            throw new ServiceException("脚本不存在");
        }
        dspEngine.unregister(script.getDataSourceId(), namespaceOf(script));

        DspScript update = new DspScript();
        update.setScriptId(scriptId);
        update.setStatus(STATUS_OFFLINE);
        update.setUpdateBy(operator);
        return dspScriptMapper.updateDspScript(update);
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
                log.error("启动加载动态脚本失败 slug={}", script.getSlug(), e);
            }
        }
        log.info("动态脚本平台初始化完成，已加载 {}/{} 个已发布脚本", success, published.size());
    }

    @Override
    public void reloadByDataSource(Long dataSourceId)
    {
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
     * 数据源ID 归一化：null 视为默认主库
     */
    private Long normalizeDataSourceId(Long dataSourceId)
    {
        return dataSourceId == null ? DspDataSource.DEFAULT_ID : dataSourceId;
    }

    /**
     * 校验绑定的数据源存在（是否停用在发布/执行时由引擎判断）
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
     * 运行时 namespace：取脚本 ID（语句 id 取脚本 slug，由平台拼接为完整语句 id）
     */
    private String namespaceOf(DspScript script)
    {
        return script == null || script.getScriptId() == null ? "" : String.valueOf(script.getScriptId());
    }
}
