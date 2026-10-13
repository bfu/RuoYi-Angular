package com.ruoyi.dsp.generator;

import java.io.Serializable;

/**
 * 脚本生成器产出的单条脚本（尚未落库）
 *
 * @author ruoyi
 */
public class DspGeneratedScript implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 操作标识：selectAll/selectPage/insertBatch/insertOne/update/delete */
    private String action;

    /** 操作中文名：查询所有/翻页查询/批量添加/单条添加/修改/删除 */
    private String actionName;

    /** MyBatis 语句类型：SELECT/INSERT/UPDATE/DELETE */
    private String sqlCommandType;

    /** 脚本名称（默认值由表名与表注释推导，可修改） */
    private String name;

    /** 脚本标识（默认值由表名与操作推导，可修改） */
    private String slug;

    /** 脚本描述 */
    private String description;

    /** 语句片段（单条语句，不写 id 与 namespace） */
    private String content;

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getActionName()
    {
        return actionName;
    }

    public void setActionName(String actionName)
    {
        this.actionName = actionName;
    }

    public String getSqlCommandType()
    {
        return sqlCommandType;
    }

    public void setSqlCommandType(String sqlCommandType)
    {
        this.sqlCommandType = sqlCommandType;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getSlug()
    {
        return slug;
    }

    public void setSlug(String slug)
    {
        this.slug = slug;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
