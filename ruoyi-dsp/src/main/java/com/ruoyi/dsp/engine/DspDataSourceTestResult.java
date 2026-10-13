package com.ruoyi.dsp.engine;

import java.io.Serializable;

/**
 * 数据源连接测试结果
 *
 * @author ruoyi
 */
public class DspDataSourceTestResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否连通 */
    private boolean success;

    /** 结果描述（成功时为数据库产品信息，失败时为错误信息） */
    private String message;

    /** 耗时(毫秒) */
    private long costMillis;

    public DspDataSourceTestResult()
    {
    }

    public DspDataSourceTestResult(boolean success, String message, long costMillis)
    {
        this.success = success;
        this.message = message;
        this.costMillis = costMillis;
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

    public long getCostMillis()
    {
        return costMillis;
    }

    public void setCostMillis(long costMillis)
    {
        this.costMillis = costMillis;
    }
}
