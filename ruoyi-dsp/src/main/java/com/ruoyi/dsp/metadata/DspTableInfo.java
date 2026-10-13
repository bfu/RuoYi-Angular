package com.ruoyi.dsp.metadata;

import java.io.Serializable;

/**
 * 数据表信息（表名、注释、类型）
 *
 * @author ruoyi
 */
public class DspTableInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 表名 */
    private String tableName;

    /** 表注释 */
    private String tableComment;

    /** 表类型（TABLE / VIEW） */
    private String tableType;

    public DspTableInfo()
    {
    }

    public DspTableInfo(String tableName, String tableComment, String tableType)
    {
        this.tableName = tableName;
        this.tableComment = tableComment;
        this.tableType = tableType;
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

    public String getTableType()
    {
        return tableType;
    }

    public void setTableType(String tableType)
    {
        this.tableType = tableType;
    }
}
