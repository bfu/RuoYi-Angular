-- ----------------------------
-- API Key 免登录授权（ruoyi-oauth）初始化脚本
-- 版本：1.0
-- 内容：密钥定义表、菜单与按钮权限
-- 前置：已执行 sql/ry_*.sql、sql/quartz.sql、sql/dsp_*.sql
-- 日期：2026-10-21
-- ----------------------------

-- ----------------------------
-- 1、API Key 定义表
-- ----------------------------
drop table if exists oauth_api_key;
create table oauth_api_key (
  key_id        bigint(20)      not null auto_increment    comment '密钥ID',
  name          varchar(128)    not null                   comment '密钥名称（调用方备注用）',
  key_hash      varchar(64)     not null                   comment '密钥摘要 SHA-256(明文密钥)，认证时按此精确匹配',
  user_id       bigint(20)      not null                   comment '绑定的用户ID（免登录即代表该用户，权限与该用户登录时一致）',
  status        char(1)         default '0'                comment '状态(0正常 1停用)',
  expires_time  datetime                                  comment '过期时间(为空表示永久有效)',
  last_used_time datetime                                 comment '最近使用时间',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''                 comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (key_id),
  unique key uk_oauth_api_key_hash (key_hash)
) engine=innodb auto_increment=1 comment = 'API Key 授权表';

-- ----------------------------
-- 2、菜单与按钮权限（开放接口目录 6 下：密钥管理 140）
-- ----------------------------
insert into sys_menu values('6',   '开放接口', '0', '5', 'oauth',    null,               '', '', 1, 0, 'M', '0', '0', '',                 'api',     'admin', sysdate(), '', null, 'API Key 授权目录');
insert into sys_menu values('140', '密钥管理', '6', '1', 'apikey', 'oauth/apikey/index', '', '', 1, 0, 'C', '0', '0', 'oauth:apikey:list', 'password', 'admin', sysdate(), '', null, 'API Key 管理菜单');

insert into sys_menu values('1400', '密钥查询', '140', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'oauth:apikey:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1401', '密钥新增', '140', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'oauth:apikey:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1402', '密钥修改', '140', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'oauth:apikey:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1403', '密钥删除', '140', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'oauth:apikey:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1404', '密钥重置', '140', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'oauth:apikey:reset',  '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 3、角色菜单关联
-- 管理员(role_id=1)拥有全部菜单，无需关联；
-- 这里按初始库惯例关联默认角色「普通角色」(role_id=2)，其余角色在「菜单管理」按需分配
-- ----------------------------
insert into sys_role_menu values ('2', '6');
insert into sys_role_menu values ('2', '140');
insert into sys_role_menu values ('2', '1400');
insert into sys_role_menu values ('2', '1401');
insert into sys_role_menu values ('2', '1402');
insert into sys_role_menu values ('2', '1403');
insert into sys_role_menu values ('2', '1404');
