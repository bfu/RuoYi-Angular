package com.ruoyi.dsp.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.generator.DspGeneratePreview;
import com.ruoyi.dsp.generator.DspGenerateRequest;
import com.ruoyi.dsp.generator.DspGeneratedScript;
import com.ruoyi.dsp.generator.DspMetaQuery;
import com.ruoyi.dsp.generator.DspScriptGenerator;
import com.ruoyi.dsp.metadata.DspColumnInfo;
import com.ruoyi.dsp.metadata.DspInstanceInfo;
import com.ruoyi.dsp.metadata.DspMetadataReader;
import com.ruoyi.dsp.metadata.DspTableInfo;
import com.ruoyi.dsp.service.IDspDataSourceService;
import com.ruoyi.dsp.service.IDspGeneratorService;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 脚本生成 服务层实现
 *
 * @author ruoyi
 */
@Service
public class DspGeneratorServiceImpl implements IDspGeneratorService
{
    @Autowired
    private DspMetadataReader dspMetadataReader;

    @Autowired
    private DspScriptGenerator dspScriptGenerator;

    @Autowired
    private IDspScriptService dspScriptService;

    @Autowired
    private IDspDataSourceService dspDataSourceService;

    @Override
    public List<DspDataSource> selectDataSourceOptions()
    {
        return dspDataSourceService.selectEnabledDataSourceList();
    }

    @Override
    public List<DspInstanceInfo> listInstances(Long dataSourceId)
    {
        return dspMetadataReader.listInstances(normalize(dataSourceId));
    }

    @Override
    public List<DspTableInfo> listTables(DspMetaQuery query)
    {
        DspMetaQuery metaQuery = query == null ? new DspMetaQuery() : query;
        return dspMetadataReader.listTables(normalize(metaQuery.getDataSourceId()), metaQuery.getCatalog(),
                metaQuery.getSchema(), metaQuery.getTableName());
    }

    @Override
    public List<DspColumnInfo> listColumns(DspMetaQuery query)
    {
        DspMetaQuery metaQuery = requireTable(query);
        return dspMetadataReader.listColumns(normalize(metaQuery.getDataSourceId()), metaQuery.getCatalog(),
                metaQuery.getSchema(), metaQuery.getTableName().trim());
    }

    @Override
    public DspGeneratePreview preview(DspMetaQuery query)
    {
        DspMetaQuery metaQuery = requireTable(query);
        Long dataSourceId = normalize(metaQuery.getDataSourceId());
        String tableName = metaQuery.getTableName().trim();
        List<DspColumnInfo> columns = dspMetadataReader.listColumns(dataSourceId, metaQuery.getCatalog(),
                metaQuery.getSchema(), tableName);
        if (columns.isEmpty())
        {
            throw new ServiceException("未读取到表结构，请确认实例与表名是否正确：" + tableName);
        }
        // 产品名决定翻页方言（MySQL limit / Oracle、SQL Server offset fetch）
        String databaseProduct = dspMetadataReader.databaseProduct(dataSourceId);
        String tableComment = resolveTableComment(dataSourceId, metaQuery.getCatalog(), metaQuery.getSchema(),
                tableName);
        DspGeneratePreview preview = new DspGeneratePreview();
        preview.setDataSourceId(dataSourceId);
        preview.setCatalog(metaQuery.getCatalog());
        preview.setSchema(metaQuery.getSchema());
        preview.setTableName(tableName);
        preview.setTableComment(tableComment);
        preview.setDatabaseProduct(databaseProduct);
        preview.setColumns(columns);
        preview.setScripts(dspScriptGenerator.generate(tableName, tableComment, columns, databaseProduct));
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DspScript> generate(DspGenerateRequest request)
    {
        if (request == null || request.getScripts() == null || request.getScripts().isEmpty())
        {
            throw new ServiceException("没有可生成的脚本");
        }
        Long dataSourceId = normalize(request.getDataSourceId());
        Set<String> slugs = new HashSet<>();
        List<DspScript> created = new ArrayList<>();
        for (DspGeneratedScript item : request.getScripts())
        {
            if (item == null || StringUtils.isBlank(item.getSlug()))
            {
                throw new ServiceException("脚本标识不能为空");
            }
            if (StringUtils.isBlank(item.getName()))
            {
                throw new ServiceException("脚本名称不能为空：" + item.getSlug());
            }
            if (StringUtils.isBlank(item.getContent()))
            {
                throw new ServiceException("脚本内容不能为空：" + item.getSlug());
            }
            String slug = item.getSlug().trim();
            if (!slugs.add(slug))
            {
                throw new ServiceException("本次提交的脚本标识重复：" + slug);
            }
            DspScript script = new DspScript();
            script.setSlug(slug);
            script.setName(item.getName().trim());
            script.setDescription(item.getDescription());
            script.setContent(item.getContent());
            script.setDataSourceId(dataSourceId);
            // 复用脚本服务：内部完成标识唯一校验，并统一置为草稿（current_version=0）
            dspScriptService.insertDspScript(script);
            created.add(script);
        }
        return created;
    }

    /**
     * 取表注释，用于生成中文脚本名称
     */
    private String resolveTableComment(Long dataSourceId, String catalog, String schema, String tableName)
    {
        for (DspTableInfo table : dspMetadataReader.listTables(dataSourceId, catalog, schema, tableName))
        {
            if (tableName.equalsIgnoreCase(table.getTableName()))
            {
                return table.getTableComment();
            }
        }
        return null;
    }

    private DspMetaQuery requireTable(DspMetaQuery query)
    {
        if (query == null || StringUtils.isBlank(query.getTableName()))
        {
            throw new ServiceException("表名不能为空");
        }
        return query;
    }

    /**
     * 数据源ID 归一化：null 视为默认主库
     */
    private Long normalize(Long dataSourceId)
    {
        return dataSourceId == null ? DspDataSource.DEFAULT_ID : dataSourceId;
    }
}
