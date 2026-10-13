-- ----------------------------
-- MyBatis 动态脚本执行平台（ruoyi-dsp）初始化脚本
-- 包含：脚本定义表、脚本版本历史表、菜单与按钮权限
-- 日期：2026-10-13
-- ----------------------------

-- ----------------------------
-- 1、动态脚本定义表
-- ----------------------------
drop table if exists dsp_script;
create table dsp_script (
  script_id       bigint(20)      not null auto_increment    comment '脚本ID',
  slug            varchar(128)    not null                   comment '唯一标识(slug)，亦作为 MyBatis namespace',
  name            varchar(128)    not null                   comment '脚本名称',
  description     varchar(500)    default ''                 comment '脚本描述',
  content         longtext                                   comment '草稿 XML 内容',
  current_version int(11)         default 0                  comment '当前已发布版本号(0=未发布)',
  status          char(1)         default '0'                comment '状态(0草稿 1已发布 2已下线)',
  create_by       varchar(64)     default ''                 comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       varchar(64)     default ''                 comment '更新者',
  update_time     datetime                                   comment '更新时间',
  remark          varchar(500)    default null               comment '备注',
  primary key (script_id),
  unique key uk_dsp_script_slug (slug)
) engine=innodb auto_increment=1 comment = '动态脚本定义表';

-- ----------------------------
-- 2、动态脚本版本历史表
-- ----------------------------
drop table if exists dsp_script_version;
create table dsp_script_version (
  version_id   bigint(20)      not null auto_increment    comment '版本ID',
  script_id    bigint(20)      not null                   comment '脚本ID',
  slug         varchar(128)    not null                   comment '脚本slug(冗余，便于运行时加载)',
  version      int(11)         not null                   comment '版本号',
  content      longtext                                   comment '发布时 XML 内容快照',
  status       char(1)         default '0'                comment '状态(0历史版本 1当前版本)',
  publish_by   varchar(64)     default ''                 comment '发布者',
  publish_time datetime                                   comment '发布时间',
  remark       varchar(500)    default null               comment '备注',
  primary key (version_id),
  unique key uk_dsp_version (script_id, version),
  key idx_dsp_version_slug (slug)
) engine=innodb auto_increment=1 comment = '动态脚本版本历史表';

-- ----------------------------
-- 3、菜单与按钮权限
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
