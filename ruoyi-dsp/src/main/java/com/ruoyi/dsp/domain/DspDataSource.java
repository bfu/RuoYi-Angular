package com.ruoyi.dsp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 数据源定义对象 dsp_data_source
 * <p>
 * 由平台在运行时按记录构建 Druid 连接池，供动态脚本绑定执行；驱动与 URL 完全由使用者填写，平台不做数据库类型校验。
 *
 * @author ruoyi
 */
public class DspDataSource extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 默认数据源ID（主库），脚本 data_source_id 为空或 0 时使用 */
    public static final Long DEFAULT_ID = 0L;

    /** 数据源ID */
    private Long dataSourceId;

    /** 数据源名称 */
    @Excel(name = "数据源名称")
    private String name;

    /** 数据库类型标识(仅展示，如 mysql/postgresql) */
    @Excel(name = "数据库类型")
    private String dbType;

    /** 驱动类名 */
    private String driverClassName;

    /** JDBC URL */
    private String url;

    /** 用户名 */
    private String username;

    /** 密码(明文) */
    private String password;

    /** 状态(0正常 1停用) */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getDataSourceId()
    {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId)
    {
        this.dataSourceId = dataSourceId;
    }

    @NotBlank(message = "数据源名称不能为空")
    @Size(min = 1, max = 128, message = "数据源名称长度不能超过128个字符")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Size(max = 32, message = "数据库类型长度不能超过32个字符")
    public String getDbType()
    {
        return dbType;
    }

    public void setDbType(String dbType)
    {
        this.dbType = dbType;
    }

    @NotBlank(message = "驱动类名不能为空")
    @Size(min = 1, max = 256, message = "驱动类名长度不能超过256个字符")
    public String getDriverClassName()
    {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName)
    {
        this.driverClassName = driverClassName;
    }

    @NotBlank(message = "JDBC URL 不能为空")
    @Size(min = 1, max = 1000, message = "JDBC URL 长度不能超过1000个字符")
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    @Size(max = 128, message = "用户名长度不能超过128个字符")
    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    @Size(max = 512, message = "密码长度不能超过512个字符")
    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
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
            .append("dataSourceId", getDataSourceId())
            .append("name", getName())
            .append("dbType", getDbType())
            .append("driverClassName", getDriverClassName())
            .append("url", getUrl())
            .append("username", getUsername())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
