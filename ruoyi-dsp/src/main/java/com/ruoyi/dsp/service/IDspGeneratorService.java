package com.ruoyi.dsp.service;

import java.util.List;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.generator.DspGeneratePreview;
import com.ruoyi.dsp.generator.DspGenerateRequest;
import com.ruoyi.dsp.generator.DspMetaQuery;
import com.ruoyi.dsp.metadata.DspColumnInfo;
import com.ruoyi.dsp.metadata.DspInstanceInfo;
import com.ruoyi.dsp.metadata.DspTableInfo;

/**
 * 脚本生成 服务层
 * <p>
 * 提供「数据源 → 实例 → 表 → 根据表结构生成 CRUD 脚本」的完整链路。
 *
 * @author ruoyi
 */
public interface IDspGeneratorService
{
    /**
     * 查询可选数据源（启用中，供下拉）
     */
    List<DspDataSource> selectDataSourceOptions();

    /**
     * 探测数据源下的实例（MySQL 库名 / PostgreSQL、Oracle 模式）
     */
    List<DspInstanceInfo> listInstances(Long dataSourceId);

    /**
     * 查询实例下的数据表
     */
    List<DspTableInfo> listTables(DspMetaQuery query);

    /**
     * 查询指定表的字段结构
     */
    List<DspColumnInfo> listColumns(DspMetaQuery query);

    /**
     * 生成预览：返回表结构与 6 条 CRUD 脚本（不落库）
     */
    DspGeneratePreview preview(DspMetaQuery query);

    /**
     * 批量保存为脚本草稿，返回新建的脚本
     */
    List<DspScript> generate(DspGenerateRequest request);
}
