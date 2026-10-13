package com.ruoyi.dsp.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 动态脚本版本历史对象 dsp_script_version
 *
 * @author ruoyi
 */
public class DspScriptVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 脚本ID */
    private Long scriptId;

    /** 脚本slug(冗余，便于运行时加载) */
    private String slug;

    /** 版本号 */
    private Integer version;

    /** 发布时 XML 内容快照 */
    private String content;

    /** 发布时绑定的数据源ID(0=默认主库) */
    private Long dataSourceId;

    /** 状态(0历史版本 1当前版本) */
    private String status;

    /** 发布者 */
    private String publishBy;

    /** 发布时间 */
    private Date publishTime;

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public Long getScriptId()
    {
        return scriptId;
    }

    public void setScriptId(Long scriptId)
    {
        this.scriptId = scriptId;
    }

    public String getSlug()
    {
        return slug;
    }

    public void setSlug(String slug)
    {
        this.slug = slug;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Long getDataSourceId()
    {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId)
    {
        this.dataSourceId = dataSourceId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPublishBy()
    {
        return publishBy;
    }

    public void setPublishBy(String publishBy)
    {
        this.publishBy = publishBy;
    }

    public Date getPublishTime()
    {
        return publishTime;
    }

    public void setPublishTime(Date publishTime)
    {
        this.publishTime = publishTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("versionId", getVersionId())
            .append("scriptId", getScriptId())
            .append("slug", getSlug())
            .append("version", getVersion())
            .append("dataSourceId", getDataSourceId())
            .append("status", getStatus())
            .append("publishBy", getPublishBy())
            .append("publishTime", getPublishTime())
            .toString();
    }
}
