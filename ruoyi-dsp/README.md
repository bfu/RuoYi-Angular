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

## 编写规范

1. 根节点必须是 `<mapper>`，且 `namespace` 必须与脚本 `slug` 完全一致（平台以 slug 作为 namespace 定位语句）。
2. **必须包含 MyBatis Mapper DOCTYPE 声明**，否则 MyBatis 解析失败；平台会在校验时给出明确提示：

   ```xml
   <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
   ```

3. `<select>` 必须显式声明 `resultType` 或 `resultMap`（结果集默认使用 `map`）。
4. 每个 `<select>/<insert>/<update>/<delete>` 需有唯一 `id`，执行时按 `statementId` 定位。
5. `#{}` 为预编译占位（推荐）；`${}` 为原始拼接，存在注入风险，平台会在校验阶段告警。

## 管理端接口

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| GET | `/dsp/script/list` | 脚本分页列表 |
| GET | `/dsp/script/{scriptId}` | 脚本详情（含草稿内容） |
| POST | `/dsp/script` | 新增脚本 |
| PUT | `/dsp/script` | 修改脚本（保存草稿） |
| DELETE | `/dsp/script/{scriptIds}` | 删除脚本 |
| POST | `/dsp/script/validate` | 校验（`{scriptId, content?}`） |
| POST | `/dsp/script/test` | 事务回滚测试（`{scriptId, statementId, params}`） |
| POST | `/dsp/script/publish` | 发布（`{scriptId}`） |
| POST | `/dsp/script/rollback` | 回滚（`{scriptId, versionId}`） |
| POST | `/dsp/script/offline` | 下线（`{scriptId}`） |
| POST | `/dsp/script/reload` | 重新加载全部已发布脚本 |
| GET | `/dsp/script/versions/{scriptId}` | 版本列表 |
| GET | `/dsp/script/version/{versionId}` | 版本详情（含内容快照） |

## 构建与测试

```bash
# 后端（RuoYi-Angular 根目录）
mvn -pl ruoyi-dsp -am test

# 前端（inforstack-ng 目录）
npm run build
npm test
```

> 动态加载与执行的核心逻辑由 `DspEngineTest`（H2 内存库，覆盖注册/执行/事务回滚/重复发布）与
> `DspValidatorTest`（纯解析，覆盖 DOCTYPE/namespace/resultType 校验）保障。
