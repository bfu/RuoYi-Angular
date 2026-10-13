package com.ruoyi.dsp.generator;

import java.io.Serializable;

/**
 * 元数据查询条件（数据源 → 实例 → 表）
 *
 * @author ruoyi
 */
public class DspMetaQuery implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 数据源 ID（0 或空为默认主库） */
    private Long dataSourceId;

    /** 实例对应的 JDBC catalog（MySQL 库名；可为空） */
    private String catalog;

    /** 实例对应的 JDBC schema（PostgreSQL/Oracle 模式；可为空） */
    private String schema;

    /** 表名（模糊搜索或精确定位） */
    private String tableName;

    public Long getDataSourceId()
    {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId)
    {
        this.dataSourceId = dataSourceId;
    }

    public String getCatalog()
    {
        return catalog;
    }

    public void setCatalog(String catalog)
    {
        this.catalog = catalog;
    }

    public String getSchema()
    {
        return schema;
    }

    public void setSchema(String schema)
    {
        this.schema = schema;
    }

    public String getTableName()
    {
        return tableName;
    }

    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }
}
