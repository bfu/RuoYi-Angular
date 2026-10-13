package com.ruoyi.dsp.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.dsp.domain.DspScriptVersion;

/**
 * 动态脚本版本历史 Mapper 接口
 *
 * @author ruoyi
 */
public interface DspScriptVersionMapper
{
    /**
     * 查询脚本的版本列表（不含 XML 内容）
     */
    List<DspScriptVersion> selectVersionListByScriptId(Long scriptId);

    /**
     * 根据版本ID查询（含 XML 内容）
     */
    DspScriptVersion selectVersionById(Long versionId);

    /**
     * 查询脚本当前版本（含 XML 内容）
     */
    DspScriptVersion selectCurrentVersionByScriptId(Long scriptId);

    /**
     * 新增版本记录
     */
    int insertVersion(DspScriptVersion version);

    /**
     * 将脚本的全部版本置为指定状态
     */
    int updateVersionStatusByScriptId(@Param("scriptId") Long scriptId, @Param("status") String status);

    /**
     * 将指定版本置为当前版本
     */
    int markVersionCurrent(Long versionId);

    /**
     * 更新脚本当前版本绑定的数据源（已发布脚本换绑后，令当前版本快照与运行时一致）
     */
    int updateCurrentVersionDataSourceId(@Param("scriptId") Long scriptId, @Param("dataSourceId") Long dataSourceId);

    /**
     * 删除脚本的全部版本
     */
    int deleteVersionByScriptId(Long scriptId);

    /**
     * 批量删除脚本版本
     */
    int deleteVersionByScriptIds(Long[] scriptIds);
}
