package com.ruoyi.dsp.generator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量生成脚本请求（前端确认预览结果后提交，脚本名称/标识可在页面上修改）
 *
 * @author ruoyi
 */
public class DspGenerateRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 脚本绑定的数据源 ID（0 或空为默认主库） */
    private Long dataSourceId;

    /** 待生成的脚本列表 */
    private List<DspGeneratedScript> scripts = new ArrayList<>();

    public Long getDataSourceId()
    {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId)
    {
        this.dataSourceId = dataSourceId;
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
