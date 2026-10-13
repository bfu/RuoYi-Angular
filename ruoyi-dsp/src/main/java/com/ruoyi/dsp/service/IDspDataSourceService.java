package com.ruoyi.dsp.service;

import java.util.List;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.engine.DspDataSourceTestResult;

/**
 * 数据源定义 服务层
 *
 * @author ruoyi
 */
public interface IDspDataSourceService
{
    /**
     * 查询数据源列表
     */
    List<DspDataSource> selectDspDataSourceList(DspDataSource dspDataSource);

    /**
     * 根据数据源ID查询（含密码）
     */
    DspDataSource selectDspDataSourceById(Long dataSourceId);

    /**
     * 查询全部正常状态的数据源（供脚本绑定下拉）
     */
    List<DspDataSource> selectEnabledDataSourceList();

    /**
     * 校验数据源名称是否唯一
     */
    boolean checkNameUnique(DspDataSource dspDataSource);

    /**
     * 新增数据源
     */
    int insertDspDataSource(DspDataSource dspDataSource);

    /**
     * 修改数据源（重建运行时连接池并重载绑定脚本）
     */
    int updateDspDataSource(DspDataSource dspDataSource);

    /**
     * 批量删除数据源（存在脚本引用时拒绝删除）
     */
    int deleteDspDataSourceByIds(Long[] dataSourceIds);

    /**
     * 测试连接
     */
    DspDataSourceTestResult testConnection(DspDataSource dspDataSource);
}
