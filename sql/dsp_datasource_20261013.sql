-- ----------------------------
-- MyBatis 动态脚本执行平台：数据源管理增量脚本
-- 包含：数据源定义表、脚本关联数据源列、菜单与按钮权限
-- 前置：已执行 dsp_20261013.sql
-- 日期：2026-10-13
-- ----------------------------

-- ----------------------------
-- 1、数据源定义表
-- ----------------------------
drop table if exists dsp_data_source;
create table dsp_data_source (
  data_source_id    bigint(20)    not null auto_increment    comment '数据源ID',
  name              varchar(128)  not null                   comment '数据源名称',
  db_type           varchar(32)   default ''                 comment '数据库类型标识(仅展示，如 mysql/postgresql)',
  driver_class_name varchar(256)  not null                   comment '驱动类名',
  url               varchar(1000) not null                   comment 'JDBC URL',
  username          varchar(128)  default ''                 comment '用户名',
  password          varchar(512)  default ''                 comment '密码(明文)',
  status            char(1)       default '0'                comment '状态(0正常 1停用)',
  create_by         varchar(64)   default ''                 comment '创建者',
  create_time       datetime                                 comment '创建时间',
  update_by         varchar(64)   default ''                 comment '更新者',
  update_time       datetime                                 comment '更新时间',
  remark            varchar(500)  default null               comment '备注',
  primary key (data_source_id),
  unique key uk_dsp_data_source_name (name)
) engine=innodb auto_increment=1 comment = '数据源定义表';

-- ----------------------------
-- 2、脚本关联数据源（0=默认主库）
-- ----------------------------
alter table dsp_script add column data_source_id bigint(20) default 0 comment '关联数据源ID(0=默认主库)';

-- ----------------------------
-- 3、菜单与按钮权限（挂在动态脚本目录 5 下）
-- ----------------------------
insert into sys_menu values('131', '数据源管理', '5', '2', 'datasource', 'dsp/datasource/index', '', '', 1, 0, 'C', '0', '0', 'dsp:datasource:list',     'server', 'admin', sysdate(), '', null, '数据源管理菜单');

insert into sys_menu values('1310', '数据源查询', '131', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1311', '数据源新增', '131', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1312', '数据源修改', '131', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1313', '数据源删除', '131', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1314', '数据源测试', '131', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:test',   '#', 'admin', sysdate(), '', null, '');
