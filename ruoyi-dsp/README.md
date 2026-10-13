# ruoyi-dsp · MyBatis 动态脚本执行平台

将 MyBatis Mapper XML 从 Java 源码中解耦：管理员在 Web 界面编写 XML、保存到数据库、校验、测试、发布，
后端在不重启应用的情况下动态加载已发布版本，业务调用方仅通过统一的 HTTP API 按 `slug` + `statementId` 执行 CRUD。

## 特性

- 脚本管理：唯一 `slug` 标识，草稿 XML 在线编辑（Monaco 编辑器）。
- 校验：XML 合法性 + MyBatis 语义校验，返回可读错误与安全告警。
- 测试：事务回滚预览——查询可看结果、DML 仅返回影响行数且不落库。
- 发布/版本历史：每次发布留档到独立版本表，版本号递增，支持回滚到任意历史版本。
- 动态热加载：启动加载全部已发布脚本，发布/回滚/下线增量更新内存注册表，无需重启。
- 统一执行 API：`POST /dsp/exec/{slug}/{statementId}`。

## 目录

```
ruoyi-dsp/
├── src/main/java/com/ruoyi/dsp/
│   ├── domain/     实体（DspScript / DspScriptVersion）
│   ├── mapper/     Mapper 接口
│   ├── service/    脚本与版本管理服务
│   ├── engine/     DspEngine（动态加载与执行）、DspValidator（校验）
│   └── controller/ DspScriptController（管理端）、DspExecuteController（执行端）
└── src/main/resources/mapper/dsp/  Mapper XML
```

## 初始化

执行 `sql/dsp_20261013.sql` 建表并注册菜单/权限。

## 权限标识

| 权限 | 说明 |
| ---- | ---- |
| `dsp:script:list` | 脚本列表 |
| `dsp:script:query` | 脚本详情/版本查询 |
| `dsp:script:add` | 新增脚本 |
| `dsp:script:edit` | 修改脚本 |
| `dsp:script:remove` | 删除脚本 |
| `dsp:script:validate` | 校验脚本 |
| `dsp:script:test` | 测试脚本（事务回滚） |
| `dsp:script:publish` | 发布脚本 |
| `dsp:script:rollback` | 回滚版本 |
| `dsp:execute` | 统一执行 API |

## 统一执行 API

```
POST /dsp/exec/{slug}/{statementId}
Content-Type: application/json

{ "id": 1, "name": "foo" }
```

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sqlCommandType": "SELECT",
    "rows": [ { "user_id": 1, "user_name": "admin" } ],
    "affectedRows": null,
    "costMillis": 3,
    "rollback": false,
    "success": true
  }
}
```

约定：`select` 返回 `rows`（每行为 Map，键为数据库原始列名，因 `mapUnderscoreToCamelCase=false`）；
`insert/update/delete` 返回 `affectedRows`。`#{}` 由 MyBatis 预编译，`${}` 存在注入风险（校验阶段告警）。
