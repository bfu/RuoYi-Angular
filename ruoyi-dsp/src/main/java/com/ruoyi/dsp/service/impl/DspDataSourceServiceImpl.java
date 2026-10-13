package com.ruoyi.dsp.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.engine.DspDataSourceManager;
import com.ruoyi.dsp.engine.DspDataSourceTestResult;
import com.ruoyi.dsp.engine.DspEngine;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;
import com.ruoyi.dsp.mapper.DspScriptMapper;
import com.ruoyi.dsp.service.IDspDataSourceService;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 数据源定义 服务层实现
 *
 * @author ruoyi
 */
@Service
public class DspDataSourceServiceImpl implements IDspDataSourceService
{
    private static final Logger log = LoggerFactory.getLogger(DspDataSourceServiceImpl.class);

    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    @Autowired
    private DspDataSourceMapper dspDataSourceMapper;

    @Autowired
    private DspScriptMapper dspScriptMapper;

    @Autowired
    private DspDataSourceManager dspDataSourceManager;

    @Autowired
    private DspEngine dspEngine;

    @Autowired
    private IDspScriptService dspScriptService;

    @Override
    public List<DspDataSource> selectDspDataSourceList(DspDataSource dspDataSource)
    {
        return dspDataSourceMapper.selectDspDataSourceList(dspDataSource);
    }

    @Override
    public DspDataSource selectDspDataSourceById(Long dataSourceId)
    {
        return dspDataSourceMapper.selectDspDataSourceById(dataSourceId);
    }

    @Override
    public List<DspDataSource> selectEnabledDataSourceList()
    {
        return dspDataSourceMapper.selectEnabledDataSourceList();
    }

    @Override
    public boolean checkNameUnique(DspDataSource dspDataSource)
    {
        Long dataSourceId = dspDataSource.getDataSourceId() == null ? -1L : dspDataSource.getDataSourceId();
        DspDataSource exist = dspDataSourceMapper.selectDspDataSourceByName(dspDataSource.getName());
        return exist == null || exist.getDataSourceId().equals(dataSourceId);
    }

    @Override
    public int insertDspDataSource(DspDataSource dspDataSource)
    {
        if (!checkNameUnique(dspDataSource))
        {
            throw new ServiceException("新增数据源'" + dspDataSource.getName() + "'失败，数据源名称已存在");
        }
        if (StringUtils.isBlank(dspDataSource.getStatus()))
        {
            dspDataSource.setStatus(STATUS_NORMAL);
        }
        dspDataSource.setCreateBy(SecurityUtils.getUsername());
        // 连接池在首次被脚本使用时按记录懒加载，无需在此建池
        return dspDataSourceMapper.insertDspDataSource(dspDataSource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDspDataSource(DspDataSource dspDataSource)
    {
        DspDataSource exist = dspDataSourceMapper.selectDspDataSourceById(dspDataSource.getDataSourceId());
        if (exist == null)
        {
            throw new ServiceException("数据源不存在");
        }
        if (!checkNameUnique(dspDataSource))
        {
            throw new ServiceException("修改数据源'" + dspDataSource.getName() + "'失败，数据源名称已存在");
        }
        dspDataSource.setUpdateBy(SecurityUtils.getUsername());
        int rows = dspDataSourceMapper.updateDspDataSource(dspDataSource);
        // 配置变更：先卸载旧语句、销毁旧连接池，再按新配置重载绑定脚本
        refreshRuntime(exist.getDataSourceId(), true);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDspDataSourceByIds(Long[] dataSourceIds)
    {
        for (Long dataSourceId : dataSourceIds)
        {
            int count = dspScriptMapper.countScriptByDataSourceId(dataSourceId);
            if (count > 0)
            {
                DspDataSource exist = dspDataSourceMapper.selectDspDataSourceById(dataSourceId);
                String name = exist == null ? String.valueOf(dataSourceId) : exist.getName();
                throw new ServiceException("数据源'" + name + "'已被 " + count + " 个脚本引用，无法删除");
            }
        }
        for (Long dataSourceId : dataSourceIds)
        {
            refreshRuntime(dataSourceId, false);
        }
        return dspDataSourceMapper.deleteDspDataSourceByIds(dataSourceIds);
    }

    @Override
    public DspDataSourceTestResult testConnection(DspDataSource dspDataSource)
    {
        DspDataSource config = dspDataSource;
        if (config == null)
        {
            return dspDataSourceManager.testConnection(null);
        }
        // 列表行只传 ID、或编辑场景未回填密码时，用库中已保存的配置补齐缺失项；
        // 表单里已填写的值（如编辑时改过的驱动/URL）不会被覆盖，仍按新配置测试
        if (config.getDataSourceId() != null)
        {
            DspDataSource exist = dspDataSourceMapper.selectDspDataSourceById(config.getDataSourceId());
            if (exist != null)
            {
                if (StringUtils.isBlank(config.getName()))
                {
                    config.setName(exist.getName());
                }
                if (StringUtils.isBlank(config.getDriverClassName()))
                {
                    config.setDriverClassName(exist.getDriverClassName());
                }
                if (StringUtils.isBlank(config.getUrl()))
                {
                    config.setUrl(exist.getUrl());
                }
                if (StringUtils.isBlank(config.getUsername()))
                {
                    config.setUsername(exist.getUsername());
                }
                if (StringUtils.isBlank(config.getPassword()))
                {
                    config.setPassword(exist.getPassword());
                }
            }
        }
        DspDataSourceTestResult result = dspDataSourceManager.testConnection(config);
        log.info("数据源连接测试 name={}, success={}, cost={}ms", config.getName(), result.isSuccess(),
                result.getCostMillis());
        return result;
    }

    /**
     * 运行时数据源变更处理：卸载引擎语句、销毁旧连接池，并按需重载绑定脚本
     *
     * @param dataSourceId 数据源ID
     * @param reload       是否在销毁后重载绑定脚本（删除场景传 false）
     */
    private void refreshRuntime(Long dataSourceId, boolean reload)
    {
        dspEngine.unregisterAll(dataSourceId);
        dspDataSourceManager.evict(dataSourceId);
        if (!reload)
        {
            return;
        }
        DspDataSource latest = dspDataSourceMapper.selectDspDataSourceById(dataSourceId);
        if (latest == null || !STATUS_NORMAL.equals(latest.getStatus()))
        {
            log.info("数据源 {} 未启用，绑定脚本暂不加载", dataSourceId);
            return;
        }
        dspScriptService.reloadByDataSource(dataSourceId);
    }
}
