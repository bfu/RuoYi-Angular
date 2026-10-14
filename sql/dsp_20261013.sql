-- ----------------------------
-- MyBatis 动态脚本执行平台（ruoyi-dsp）初始化脚本
-- 版本：1.0
-- 内容：脚本定义表、脚本版本历史表、数据源定义表、菜单与按钮权限
-- 前置：已执行 sql/ry_*.sql、sql/quartz.sql
-- 说明：由 dsp_datasource_20261013.sql、dsp_generator_20261013.sql、
--        dsp_version_datasource_20261013.sql 三个增量脚本合并而来，面向全新部署（drop 后重建）
-- 日期：2026-10-13
-- ----------------------------

-- ----------------------------
-- 1、动态脚本定义表
-- ----------------------------
drop table if exists dsp_script;
create table dsp_script (
  script_id       bigint(20)      not null auto_increment    comment '脚本ID',
  slug            varchar(128)    not null                   comment '唯一标识(slug)，作为语句 id 与执行入口',
  name            varchar(128)    not null                   comment '脚本名称',
  description     varchar(500)    default ''                 comment '脚本描述',
  content         longtext                                   comment '草稿内容：单条语句片段（语句 id 与 mapper 包装由后端补全）',
  current_version int(11)         default 0                  comment '当前已发布版本号(0=未发布)',
  status          char(1)         default '0'                comment '状态(0草稿 1已发布 2已下线)',
  create_by       varchar(64)     default ''                 comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       varchar(64)     default ''                 comment '更新者',
  update_time     datetime                                   comment '更新时间',
  remark          varchar(500)    default null               comment '备注',
  data_source_id  bigint(20)      default 0                  comment '关联数据源ID(0=默认主库)',
  primary key (script_id),
  unique key uk_dsp_script_slug (slug)
) engine=innodb auto_increment=1 comment = '动态脚本定义表';

-- ----------------------------
-- 2、动态脚本版本历史表
-- ----------------------------
drop table if exists dsp_script_version;
create table dsp_script_version (
  version_id     bigint(20)      not null auto_increment    comment '版本ID',
  script_id      bigint(20)      not null                   comment '脚本ID',
  slug           varchar(128)    not null                   comment '脚本slug(冗余，便于版本查询与运行时加载)',
  version        int(11)         not null                   comment '版本号',
  content        longtext                                   comment '发布时语句片段快照',
  status         char(1)         default '0'                comment '状态(0历史版本 1当前版本)',
  publish_by     varchar(64)     default ''                 comment '发布者',
  publish_time   datetime                                   comment '发布时间',
  remark         varchar(500)    default null               comment '备注',
  data_source_id bigint(20)      default 0                  comment '发布时绑定的数据源ID(0=默认主库)',
  primary key (version_id),
  unique key uk_dsp_version (script_id, version),
  key idx_dsp_version_slug (slug),
  key idx_dsp_version_datasource (data_source_id)
) engine=innodb auto_increment=1 comment = '动态脚本版本历史表';

-- ----------------------------
-- 3、数据源定义表
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
-- 4、菜单与按钮权限（动态脚本目录 5 下：脚本管理 130、数据源管理 131、脚本生成 132）
-- ----------------------------
insert into sys_menu values('5',   '动态脚本', '0', '4', 'dsp',    null,               '', '', 1, 0, 'M', '0', '0', '',                  'code',  'admin', sysdate(), '', null, '动态脚本目录');
insert into sys_menu values('130', '脚本管理', '5', '1', 'script', 'dsp/script/index', '', '', 1, 0, 'C', '0', '0', 'dsp:script:list',     'edit',  'admin', sysdate(), '', null, '动态脚本管理菜单');

insert into sys_menu values('1300', '脚本查询', '130', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:query',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1301', '脚本新增', '130', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:add',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1302', '脚本修改', '130', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:edit',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1303', '脚本删除', '130', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:remove',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1304', '脚本校验', '130', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:validate', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1305', '脚本测试', '130', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:test',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1306', '脚本发布', '130', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:publish',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1307', '脚本回滚', '130', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:script:rollback', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1308', '脚本执行', '130', '9', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:execute',         '#', 'admin', sysdate(), '', null, '');

insert into sys_menu values('131', '数据源管理', '5', '2', 'datasource', 'dsp/datasource/index', '', '', 1, 0, 'C', '0', '0', 'dsp:datasource:list',     'server', 'admin', sysdate(), '', null, '数据源管理菜单');

insert into sys_menu values('1310', '数据源查询', '131', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1311', '数据源新增', '131', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1312', '数据源修改', '131', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1313', '数据源删除', '131', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1314', '数据源测试', '131', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:datasource:test',   '#', 'admin', sysdate(), '', null, '');

insert into sys_menu values('132', '脚本生成', '5', '3', 'generator', 'dsp/generator/index', '', '', 1, 0, 'C', '0', '0', 'dsp:generator:list',     'tool', 'admin', sysdate(), '', null, '脚本生成菜单：按表结构生成 CRUD 脚本');

insert into sys_menu values('1320', '生成查询', '132', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:generator:query',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1321', '生成预览', '132', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:generator:preview',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1322', '脚本生成', '132', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:generator:generate', '#', 'admin', sysdate(), '', null, '');
