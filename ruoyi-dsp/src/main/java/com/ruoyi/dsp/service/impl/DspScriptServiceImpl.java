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
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.domain.DspScriptVersion;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.engine.DspExecutionResult;
import com.ruoyi.dsp.engine.DspValidationResult;
import com.ruoyi.dsp.engine.DspValidator;
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
        // slug 不可变，禁止通过更新语句修改
        dspScript.setSlug(null);
        dspScript.setUpdateBy(SecurityUtils.getUsername());
        return dspScriptMapper.updateDspScript(dspScript);
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
                dspEngine.unregister(exist.getSlug());
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
        return dspValidator.validate(exist.getSlug(), scriptContent);
    }

    @Override
    public DspExecutionResult testScript(Long scriptId, String statementId, Map<String, Object> params)
    {
        DspScript exist = dspScriptMapper.selectDspScriptById(scriptId);
        if (exist == null)
        {
            throw new ServiceException("脚本不存在");
        }
        DspValidationResult validation = dspValidator.validate(exist.getSlug(), exist.getContent());
        if (!validation.isValid())
        {
            throw new ServiceException("脚本校验未通过：" + String.join("；", validation.getErrors()));
        }
        String target = StringUtils.isNotBlank(statementId) ? statementId
                : (validation.getStatements().isEmpty() ? null : validation.getStatements().get(0));
        if (StringUtils.isBlank(target) || !validation.getStatements().contains(target))
        {
            throw new ServiceException("语句不存在：" + target);
        }
        // 使用临时命名空间，避免影响已发布版本
        String testNamespace = "__dsp_test_" + scriptId + "_" + System.nanoTime();
        dspEngine.register(testNamespace, exist.getContent());
        try
        {
            return dspEngine.execute(testNamespace, target, params, true);
        }
        finally
        {
            dspEngine.unregister(testNamespace);
        }
    }

    @Override
    public DspExecutionResult execute(String slug, String statementId, Map<String, Object> params)
    {
        // slug 即运行时 namespace，完整语句 id 为 slug.语句名（由引擎拼接）
        if (StringUtils.isBlank(slug) || !dspEngine.hasNamespace(slug))
        {
            throw new ServiceException("脚本未发布或不可用：" + slug);
        }
        return dspEngine.execute(slug, statementId, params, false);
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
        DspValidationResult validation = dspValidator.validate(script.getSlug(), script.getContent());
        if (!validation.isValid())
        {
            throw new ServiceException("脚本校验未通过：" + String.join("；", validation.getErrors()));
        }
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

        dspEngine.register(script.getSlug(), script.getContent());
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
        DspValidationResult validation = dspValidator.validate(script.getSlug(), version.getContent());
        if (!validation.isValid())
        {
            throw new ServiceException("目标版本内容校验未通过：" + String.join("；", validation.getErrors()));
        }
        dspScriptVersionMapper.updateVersionStatusByScriptId(scriptId, "0");
        dspScriptVersionMapper.markVersionCurrent(versionId);

        DspScript update = new DspScript();
        update.setScriptId(scriptId);
        update.setContent(version.getContent());
        update.setCurrentVersion(version.getVersion());
        update.setStatus(STATUS_PUBLISHED);
        update.setUpdateBy(operator);
        dspScriptMapper.updateDspScript(update);

        dspEngine.register(script.getSlug(), version.getContent());
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
        dspEngine.unregister(script.getSlug());

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
                dspEngine.register(script.getSlug(), script.getContent());
                success++;
            }
            catch (Exception e)
            {
                log.error("启动加载动态脚本失败 slug={}", script.getSlug(), e);
            }
        }
        log.info("动态脚本平台初始化完成，已加载 {}/{} 个已发布脚本", success, published.size());
    }
}
