package com.ruoyi.dsp.mapper;

import java.util.List;
import com.ruoyi.dsp.domain.DspScript;

/**
 * 动态脚本定义 Mapper 接口
 *
 * @author ruoyi
 */
public interface DspScriptMapper
{
    /**
     * 查询脚本列表（不含 XML 内容，供管理端分页展示）
     */
    List<DspScript> selectDspScriptList(DspScript dspScript);

    /**
     * 根据脚本ID查询（含 XML 内容）
     */
    DspScript selectDspScriptById(Long scriptId);

    /**
     * 根据 slug 查询（含 XML 内容）
     */
    DspScript selectDspScriptBySlug(String slug);

    /**
     * 查询全部已发布脚本（含 XML 内容），供启动/刷新时热加载
     */
    List<DspScript> selectPublishedScriptList();

    /**
     * 新增脚本
     */
    int insertDspScript(DspScript dspScript);

    /**
     * 修改脚本（动态更新非空字段）
     */
    int updateDspScript(DspScript dspScript);

    /**
     * 批量删除脚本
     */
    int deleteDspScriptByIds(Long[] scriptIds);

    /**
     * 删除脚本
     */
    int deleteDspScriptById(Long scriptId);
}
