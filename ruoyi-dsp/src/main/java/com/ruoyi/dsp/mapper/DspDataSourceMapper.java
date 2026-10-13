package com.ruoyi.dsp.mapper;

import java.util.List;
import com.ruoyi.dsp.domain.DspDataSource;

/**
 * 数据源定义 Mapper 接口
 *
 * @author ruoyi
 */
public interface DspDataSourceMapper
{
    /**
     * 查询数据源列表（不含密码，供管理端分页展示）
     */
    List<DspDataSource> selectDspDataSourceList(DspDataSource dspDataSource);

    /**
     * 根据数据源ID查询（含密码，供编辑与建池）
     */
    DspDataSource selectDspDataSourceById(Long dataSourceId);

    /**
     * 查询全部正常状态的数据源（不含密码，供脚本绑定下拉）
     */
    List<DspDataSource> selectEnabledDataSourceList();

    /**
     * 根据名称查询数据源（用于名称唯一校验）
     */
    DspDataSource selectDspDataSourceByName(String name);

    /**
     * 新增数据源
     */
    int insertDspDataSource(DspDataSource dspDataSource);

    /**
     * 修改数据源（动态更新非空字段）
     */
    int updateDspDataSource(DspDataSource dspDataSource);

    /**
     * 批量删除数据源
     */
    int deleteDspDataSourceByIds(Long[] dataSourceIds);
}
