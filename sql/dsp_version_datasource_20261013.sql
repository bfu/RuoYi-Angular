-- ----------------------------
-- MyBatis 动态脚本执行平台：版本快照携带数据源
-- 前置：已执行 dsp_20261013.sql、dsp_datasource_20261013.sql
-- 日期：2026-10-13
-- ----------------------------

-- ----------------------------
-- 1、版本历史表增加「发布时绑定的数据源」
-- ----------------------------
alter table dsp_script_version add column data_source_id bigint(20) default 0 comment '发布时绑定的数据源ID(0=默认主库)';

-- ----------------------------
-- 2、历史版本回填
-- 无法还原各版本发布时的真实数据源，取脚本当前绑定值近似，
-- 避免回滚到历史版本时错误地切回默认主库。
-- ----------------------------
update dsp_script_version v
  join dsp_script s on v.script_id = s.script_id
  set v.data_source_id = ifnull(s.data_source_id, 0);

-- ----------------------------
-- 3、便于按数据源统计版本
-- ----------------------------
alter table dsp_script_version add key idx_dsp_version_datasource (data_source_id);
