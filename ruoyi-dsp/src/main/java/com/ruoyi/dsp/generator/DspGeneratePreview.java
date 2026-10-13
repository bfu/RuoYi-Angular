package com.ruoyi.dsp.generator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.dsp.metadata.DspColumnInfo;

/**
 * 脚本生成预览结果：表结构 + 生成的脚本列表（不落库，由前端确认后再提交生成）
 *
 * @author ruoyi
 */
public class DspGeneratePreview implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 数据源 ID */
    private Long dataSourceId;

    /** 实例 catalog */
    private String catalog;

    /** 实例 schema */
    private String schema;

    /** 表名 */
    private String tableName;

    /** 表注释 */
    private String tableComment;

    /** 数据库产品名（决定翻页方言） */
    private String databaseProduct;

    /** 表结构 */
    private List<DspColumnInfo> columns = new ArrayList<>();

    /** 生成的脚本 */
    private List<DspGeneratedScript> scripts = new ArrayList<>();

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

    public String getTableComment()
    {
        return tableComment;
    }

    public void setTableComment(String tableComment)
    {
        this.tableComment = tableComment;
    }

    public String getDatabaseProduct()
    {
        return databaseProduct;
    }

    public void setDatabaseProduct(String databaseProduct)
    {
        this.databaseProduct = databaseProduct;
    }

    public List<DspColumnInfo> getColumns()
    {
        return columns;
    }

    public void setColumns(List<DspColumnInfo> columns)
    {
        this.columns = columns;
    }

    public List<DspGeneratedScript> getScripts()
    {
        return scripts;
    }

    public void setScripts(List<DspGeneratedScript> scripts)
    {
        this.scripts = scripts;
    }
}
