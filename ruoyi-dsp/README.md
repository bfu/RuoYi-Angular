# ruoyi-dsp · MyBatis 动态脚本执行平台

将 MyBatis Mapper 语句从 Java 源码中解耦：管理员在 Web 界面编写语句片段、保存到数据库、校验、测试、发布，
后端在不重启应用的情况下动态加载已发布版本，业务调用方仅通过统一的 HTTP API 按 `slug` + `statementId` 执行 CRUD。

## 特性

- 脚本管理：唯一 `slug` 标识（即语句 id），单条语句片段在线编辑（Monaco 编辑器）。
- 校验：XML 合法性 + MyBatis 语义校验，返回可读错误与安全告警。
- 测试：事务回滚预览——查询可看结果、DML 仅返回影响行数且不落库。
- 发布/版本历史：每次发布留档到独立版本表，版本号递增，支持回滚到任意历史版本。
- 动态热加载：启动加载全部已发布脚本，发布/回滚/换绑/下线/删除增量更新内存注册表，无需重启。
- 多实例一致：运行时注册在各实例内存中，变更经 Redis 广播通知其余实例重载（见「多实例部署」）。
- 统一执行 API：`POST /dsp/exec/{slug}`。

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

在 `sql/ry_*.sql`、`sql/quartz.sql` 之后执行 `sql/dsp_1.0.sql`（ruoyi-dsp 1.0 基线脚本）建表并注册菜单/权限。

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
POST /dsp/exec/{slug}
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
`slug` 即语句 id：平台以**脚本 ID** 作为 namespace、以 `slug` 作为语句 id，
拼成完整语句 id `脚本ID.slug`（例如脚本 ID=12、slug=`order_query` → `12.order_query`），
调用方只需按 `slug` 调用。

## 编写规范

**一个脚本只写一条语句**，且**不写 id**；xml 声明、DOCTYPE、`<mapper>` 标签、namespace 与语句 id
均由平台补全：

```xml
<select resultType="map">
  select 1 as id
</select>
```

1. 语句 id 由平台填充为脚本 `slug`，运行时 namespace 取脚本 ID：内容中不写 `id`、不写 `namespace`，
   也不写 `slug` 前缀（内容里写了 `id` 会被忽略并给出提示）。
2. 一个脚本只允许一条 `<select>/<insert>/<update>/<delete>` 语句，多条会被校验拒绝。
3. 历史内容若仍带 `<?xml>`、DOCTYPE 或 `<mapper>` 包装，平台会自动剥离并在校验结果中给出提示。
4. `<select>` 必须显式声明 `resultType` 或 `resultMap`（结果集默认使用 `map`）。
5. `#{}` 为预编译占位（推荐）；`${}` 为原始拼接，存在注入风险，平台会在校验阶段告警。
6. `LIMIT` / `OFFSET`（翻页）处的占位符只接受整数：平台会把该处占位符上的整数字符串自动规整为整数，
   因此测试抽屉里的 `{"size": "10"}` 与 `GET /dsp/exec/{slug}?size=10`（URL 查询串天然是字符串）都能正常执行；
   其余位置的参数保持原样，不会被转成数字。翻页参数取非整数时直接返回可读错误，不再落到数据库报语法错误。

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
| POST | `/dsp/script/reload` | 重新加载全部已发布脚本（先清后建，并广播其余实例） |
| GET | `/dsp/script/versions/{scriptId}` | 版本列表 |
| GET | `/dsp/script/version/{versionId}` | 版本详情（含内容快照） |

## 多实例部署

运行时语句注册（MyBatis Configuration）只在各实例的 JVM 内存中，数据库里只有脚本定义与版本。因此脚本的发布、
回滚、换绑、下线、删除以及数据源连接配置变更，都会在数据库事务提交后经 Redis 频道广播，其余实例收到后按数据库
重建本地注册，无需逐个实例调用接口。整体重载（`/dsp/script/reload`）同样会广播，且会先清空本地注册再按库重建，
库里已删除或已下线的脚本不会残留在内存中继续可执行。

| 配置项 | 默认值 | 说明 |
| ---- | ---- | ---- |
| `dsp.cluster.enabled` | `true` | 是否开启广播；单实例部署可关闭，关闭后退化为「只在本实例生效」 |
| `dsp.cluster.reload-channel` | `ruoyi:dsp:reload` | 广播频道；同一 Redis 上的多个应用需分别配置，避免相互触发重载 |

广播失败（如 Redis 不可用）只记录错误日志，不影响本实例的变更；此时其余实例会保持旧状态，可调用
`POST /dsp/script/reload` 或重启修正。

## 构建与测试

```bash
# 后端（RuoYi-Angular 根目录）
mvn -pl ruoyi-dsp -am test

# 前端（inforstack-ng 目录）
npm run build
npm test
```

> 动态加载与执行的核心逻辑由 `DspEngineTest`（H2 内存库，覆盖注册/执行/事务回滚/重复发布）与
> `DspValidatorTest`（纯解析，覆盖片段包装/遗留包装剥离/namespace=脚本ID/语句 id=slug/多语句拒绝）保障。
