package com.ruoyi.dsp.metadata;

import java.io.Serializable;

/**
 * 数据表字段信息，用于生成脚本与页面展示表结构
 *
 * @author ruoyi
 */
public class DspColumnInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 字段名 */
    private String columnName;

    /** 字段注释 */
    private String columnComment;

    /** 字段类型（含长度，如 varchar(64)） */
    private String columnType;

    /** 对应 Java 类型 */
    private String javaType;

    /** 对应 Java 属性名（下划线转驼峰） */
    private String javaField;

    /** 是否主键 */
    private boolean primaryKey;

    /** 是否必填（非空且非主键） */
    private boolean required;

    /** 是否自增 */
    private boolean autoIncrement;

    /** 字段顺序 */
    private int ordinal;

    public String getColumnName()
    {
        return columnName;
    }

    public void setColumnName(String columnName)
    {
        this.columnName = columnName;
    }

    public String getColumnComment()
    {
        return columnComment;
    }

    public void setColumnComment(String columnComment)
    {
        this.columnComment = columnComment;
    }

    public String getColumnType()
    {
        return columnType;
    }

    public void setColumnType(String columnType)
    {
        this.columnType = columnType;
    }

    public String getJavaType()
    {
        return javaType;
    }

    public void setJavaType(String javaType)
    {
        this.javaType = javaType;
    }

    public String getJavaField()
    {
        return javaField;
    }

    public void setJavaField(String javaField)
    {
        this.javaField = javaField;
    }

    public boolean isPrimaryKey()
    {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey)
    {
        this.primaryKey = primaryKey;
    }

    public boolean isRequired()
    {
        return required;
    }

    public void setRequired(boolean required)
    {
        this.required = required;
    }

    public boolean isAutoIncrement()
    {
        return autoIncrement;
    }

    public void setAutoIncrement(boolean autoIncrement)
    {
        this.autoIncrement = autoIncrement;
    }

    public int getOrdinal()
    {
        return ordinal;
    }

    public void setOrdinal(int ordinal)
    {
        this.ordinal = ordinal;
    }
}
