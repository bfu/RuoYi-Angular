package com.ruoyi.dsp.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.dsp.domain.DspScript;
import com.ruoyi.dsp.domain.DspScriptVersion;
import com.ruoyi.dsp.engine.DspExecutionResult;
import com.ruoyi.dsp.engine.DspValidationResult;

/**
 * 动态脚本定义 服务层
 *
 * @author ruoyi
 */
public interface IDspScriptService
{
    /**
     * 查询脚本列表
     */
    List<DspScript> selectDspScriptList(DspScript dspScript);

    /**
     * 根据脚本ID查询（含内容）
     */
    DspScript selectDspScriptById(Long scriptId);

    /**
     * 校验脚本标识是否唯一
     */
    boolean checkSlugUnique(DspScript dspScript);

    /**
     * 新增脚本
     */
    int insertDspScript(DspScript dspScript);

    /**
     * 修改脚本（草稿）
     */
    int updateDspScript(DspScript dspScript);

    /**
     * 批量删除脚本（同时卸载引擎中已发布的语句）
     */
    int deleteDspScriptByIds(Long[] scriptIds);

    /**
     * 校验脚本内容（content 为空时校验库中草稿），方言按脚本当前绑定的数据源判定
     */
    DspValidationResult validateScript(Long scriptId, String content);

    /**
     * 校验脚本内容，并可按指定数据源判定方言
     * <p>
     * 回滚场景校验的是历史版本内容，其实际执行位置是版本绑定的数据源，需要用该数据源判定方言。
     *
     * @param scriptId     脚本 ID
     * @param content      语句片段，为空时校验库中草稿
     * @param dataSourceId 用于方言判定的数据源 ID（{@code 0} 为默认主库）；
     *                     为 {@code null} 时用脚本当前绑定的数据源
     */
    DspValidationResult validateScript(Long scriptId, String content, Long dataSourceId);

    /**
     * 事务回滚测试脚本语句（使用草稿内容，隔离到临时命名空间）
     */
    DspExecutionResult testScript(Long scriptId, String statementId, Map<String, Object> params);

    /**
     * 执行已发布脚本的语句（单语句脚本，按 slug 定位；运行时 namespace 取脚本 ID）
     */
    DspExecutionResult execute(String slug, Map<String, Object> params);

    /**
     * 发布脚本，返回新版本号
     */
    int publish(Long scriptId, String operator);

    /**
     * 回滚到指定历史版本，返回该版本号
     */
    int rollback(Long scriptId, Long versionId, String operator);

    /**
     * 下线脚本（卸载引擎中的语句）
     */
    int offline(Long scriptId, String operator);

    /**
     * 查询脚本版本列表
     */
    List<DspScriptVersion> selectVersionList(Long scriptId);

    /**
     * 查询版本详情（含内容）
     */
    DspScriptVersion selectVersionById(Long versionId);

    /**
     * 重新加载全部已发布脚本到引擎：先清空运行时注册，再按数据库重建
     * （数据库里已删除/已下线的脚本不能继续可执行）
     */
    void reloadAll();

    /**
     * 重新加载全部已发布脚本，并广播其余实例一起重载
     */
    void reloadAllAndBroadcast();

    /**
     * 重新加载指定数据源下的已发布脚本：先清空该数据源的运行时注册，再按数据库重建
     *
     * @param dataSourceId 数据源ID
     */
    void reloadByDataSource(Long dataSourceId);
}
