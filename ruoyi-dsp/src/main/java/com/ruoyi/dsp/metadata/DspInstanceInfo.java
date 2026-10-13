package com.ruoyi.dsp.metadata;

import java.io.Serializable;

/**
 * 数据源下的实例信息（MySQL 库名 / PostgreSQL、Oracle 模式）
 * <p>
 * 实例用 JDBC 的 catalog 与 schema 共同定位：MySQL 取 catalog（库名），PostgreSQL、Oracle 取 schema。
 *
 * @author ruoyi
 */
public class DspInstanceInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 展示名称 */
    private String name;

    /** JDBC catalog（可为空） */
    private String catalog;

    /** JDBC schema（可为空） */
    private String schema;

    /** 实例类型：catalog / schema */
    private String type;

    /** 是否为当前连接默认实例 */
    private boolean current;

    public DspInstanceInfo()
    {
    }

    public DspInstanceInfo(String name, String catalog, String schema, String type, boolean current)
    {
        this.name = name;
        this.catalog = catalog;
        this.schema = schema;
        this.type = type;
        this.current = current;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
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

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public boolean isCurrent()
    {
        return current;
    }

    public void setCurrent(boolean current)
    {
        this.current = current;
    }
}
