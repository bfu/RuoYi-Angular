package com.ruoyi.dsp.cluster;

/**
 * 运行时重载范围
 *
 * @author ruoyi
 */
public enum DspReloadScope
{
    /** 全部数据源（手动整体重载） */
    ALL,

    /** 指定数据源的脚本变更：发布、回滚、换绑、下线、删除 */
    SCRIPT,

    /** 指定数据源的连接配置变更：除重载脚本外还需重建连接池 */
    DATASOURCE
}
