package com.ruoyi.dsp.engine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态脚本校验结果
 *
 * @author ruoyi
 */
public class DspValidationResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否校验通过 */
    private boolean valid = true;

    /** 运行时命名空间（由脚本 ID 生成） */
    private String namespace;

    /** 解析出的语句 id 集合 */
    private List<String> statements = new ArrayList<>();

    /** 错误信息 */
    private List<String> errors = new ArrayList<>();

    /** 告警信息 */
    private List<String> warnings = new ArrayList<>();

    public boolean isValid()
    {
        return valid;
    }

    public void setValid(boolean valid)
    {
        this.valid = valid;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public void setNamespace(String namespace)
    {
        this.namespace = namespace;
    }

    public List<String> getStatements()
    {
        return statements;
    }

    public void setStatements(List<String> statements)
    {
        this.statements = statements;
    }

    public List<String> getErrors()
    {
        return errors;
    }

    public void setErrors(List<String> errors)
    {
        this.errors = errors;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings;
    }

    public void addError(String error)
    {
        this.errors.add(error);
        this.valid = false;
    }

    public void addWarning(String warning)
    {
        this.warnings.add(warning);
    }
}
