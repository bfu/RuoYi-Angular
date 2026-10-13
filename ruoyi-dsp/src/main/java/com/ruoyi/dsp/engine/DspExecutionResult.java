package com.ruoyi.dsp.engine;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 动态脚本执行结果
 *
 * @author ruoyi
 */
public class DspExecutionResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 语句类型(SELECT/INSERT/UPDATE/DELETE) */
    private String sqlCommandType;

    /** 查询结果集（仅 SELECT，每行为 Map） */
    private List<Map<String, Object>> rows;

    /** 影响行数（仅 INSERT/UPDATE/DELETE） */
    private Integer affectedRows;

    /** 耗时(毫秒) */
    private long costMillis;

    /** 是否为事务回滚预览 */
    private boolean rollback;

    /** 是否执行成功 */
    private boolean success = true;

    /** 提示信息 */
    private String message;

    public String getSqlCommandType()
    {
        return sqlCommandType;
    }

    public void setSqlCommandType(String sqlCommandType)
    {
        this.sqlCommandType = sqlCommandType;
    }

    public List<Map<String, Object>> getRows()
    {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows)
    {
        this.rows = rows;
    }

    public Integer getAffectedRows()
    {
        return affectedRows;
    }

    public void setAffectedRows(Integer affectedRows)
    {
        this.affectedRows = affectedRows;
    }

    public long getCostMillis()
    {
        return costMillis;
    }

    public void setCostMillis(long costMillis)
    {
        this.costMillis = costMillis;
    }

    public boolean isRollback()
    {
        return rollback;
    }

    public void setRollback(boolean rollback)
    {
        this.rollback = rollback;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public void setSuccess(boolean success)
    {
        this.success = success;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
