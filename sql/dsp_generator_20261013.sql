-- ----------------------------
-- MyBatis 动态脚本执行平台：脚本生成菜单增量脚本
-- 包含：脚本生成菜单与按钮权限（挂在动态脚本目录 5 下）
-- 前置：已执行 dsp_20261013.sql、dsp_datasource_20261013.sql
-- 日期：2026-10-13
-- ----------------------------

insert into sys_menu values('132', '脚本生成', '5', '3', 'generator', 'dsp/generator/index', '', '', 1, 0, 'C', '0', '0', 'dsp:generator:list',     'tool', 'admin', sysdate(), '', null, '脚本生成菜单：按表结构生成 CRUD 脚本');

insert into sys_menu values('1320', '生成查询', '132', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:generator:query',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1321', '生成预览', '132', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:generator:preview',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1322', '脚本生成', '132', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'dsp:generator:generate', '#', 'admin', sysdate(), '', null, '');
