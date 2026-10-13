package com.ruoyi.dsp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 动态脚本定义对象 dsp_script
 *
 * @author ruoyi
 */
public class DspScript extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 脚本ID */
    private Long scriptId;

    /** 唯一标识(slug)，亦作为 MyBatis namespace */
    @Excel(name = "脚本标识")
    private String slug;

    /** 脚本名称 */
    @Excel(name = "脚本名称")
    private String name;

    /** 脚本描述 */
    private String description;

    /** 草稿 XML 内容 */
    private String content;

    /** 当前已发布版本号(0=未发布) */
    @Excel(name = "当前版本")
    private Integer currentVersion;

    /** 状态(0草稿 1已发布 2已下线) */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已发布,2=已下线")
    private String status;

    public Long getScriptId()
    {
        return scriptId;
    }

    public void setScriptId(Long scriptId)
    {
        this.scriptId = scriptId;
    }

    @NotBlank(message = "脚本标识不能为空")
    @Size(min = 1, max = 128, message = "脚本标识长度不能超过128个字符")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "脚本标识只能以字母开头，且仅包含字母、数字、下划线和中划线")
    public String getSlug()
    {
        return slug;
    }

    public void setSlug(String slug)
    {
        this.slug = slug;
    }

    @NotBlank(message = "脚本名称不能为空")
    @Size(min = 1, max = 128, message = "脚本名称长度不能超过128个字符")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
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

    public Integer getCurrentVersion()
    {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion)
    {
        this.currentVersion = currentVersion;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("scriptId", getScriptId())
            .append("slug", getSlug())
            .append("name", getName())
            .append("description", getDescription())
            .append("currentVersion", getCurrentVersion())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
