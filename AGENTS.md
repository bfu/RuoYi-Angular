# AGENTS.md — RuoYi-Angular（后端仓库）

RuoYi-Vue 3.9.2 的衍生**后端**仓库：Spring Boot 4.1 / JDK 17 / MyBatis 多模块，官方接口**零改造**，因此官方 Vue 前端与 Angular 前端可共用同一套后端。

**本仓库只有后端**：Angular 前端在独立仓库 [`bfu/inforstack-ng`](https://github.com/bfu/inforstack-ng)（同级目录 `../inforstack-ng`），本仓库不包含任何前端页面代码。

## 一、仓库角色与边界

| 路径 | 角色 | 可改动 |
| ---- | ---- | ------ |
| 本仓库（`ruoyi-admin`/`framework`/`system`/`common`/`quartz`/`generator`） | 从上游复制过来的官方后端代码 | ⚠️ 非必要不改（优先把功能落在衍生部分） |
| `ruoyi-dsp/` | 本仓库的**核心定制**：MyBatis 动态脚本执行平台 | ✅ 允许 |
| `ruoyi-generator/src/main/resources/vm/angular/` | Angular（NG-ZORRO）代码生成模板 | ✅ 允许 |
| `sql/dsp_*.sql` | dsp 平台建表 + 菜单权限脚本 | ✅ 允许 |
| `ruoyi-vue3/` | 上游 Vue3 前端的本地克隆，被 `.gitignore` 忽略、不入库 | ❌ 只读对照 |
| `../RuoYi-Vue`、`../RuoYi-Vue3` | 上游参照仓库 | ❌ 只读，禁止任何改动 |

## 二、模块结构

| 模块 | 职责 |
| ---- | ---- |
| `ruoyi-admin` | 启动入口 `com.ruoyi.RuoYiApplication`（`@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`）；配置文件与 `banner.txt` |
| `ruoyi-framework` | 安全（Spring Security + JWT）、拦截器、多数据源、AOP；`@MapperScan("com.ruoyi.**.mapper")` 位于 `framework/config/ApplicationConfig.java` |
| `ruoyi-system` | 系统业务：用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 公告 / 日志 |
| `ruoyi-common` | 通用工具、注解、常量、异常 |
| `ruoyi-quartz` | 定时任务与调度日志 |
| `ruoyi-generator` | 代码生成（含 `vm/angular` 模板） |
| `ruoyi-dsp` | 动态脚本执行平台（本项目定制，见第三节） |

约定：MyBatis 映射由 `application.yml` 的 `mybatis.mapperLocations: classpath*:mapper/**/*Mapper.xml` + `typeAliasesPackage: com.ruoyi.**.domain` 自动加载，组件扫描覆盖 `com.ruoyi.**`；因此**新增代码只要包名放对就无需改配置**（`ruoyi-dsp` 就是这样接入的：仅需在 `ruoyi-admin/pom.xml` 声明依赖 + 根 `pom.xml` 的 `<modules>` 登记）。

## 三、ruoyi-dsp：MyBatis 动态脚本执行平台（核心定制）

把 Mapper 语句从 Java 源码中解耦：管理员在 Web 端编写**单条语句片段**存库、校验、测试、发布，后端**不重启**热加载已发布版本；调用方按 `slug` 经统一 HTTP API 执行 CRUD。详细说明见 `ruoyi-dsp/README.md`。

包结构（`ruoyi-dsp/src/main/java/com/ruoyi/dsp/`）：

| 包 | 内容 |
| -- | ---- |
| `controller` | `DspScriptController`(管理端) / `DspExecuteController`(执行端) / `DspDataSourceController` / `DspGeneratorController` |
| `service(.impl)` | `DspScriptServiceImpl`(发布/回滚/下线/重载/执行/校验) · `DspScriptLoader`(启动时 `reloadAll`) · `DspAfterCommit`(事务提交后再生效) |
| `engine` | `DspEngine`(运行时注册与执行) · `DspValidator`(静态校验 + 方言告警) · `DspDataSourceManager`(各数据源 Druid 池 / SqlSessionTemplate / 事务管理器) |
| `cluster` | Redis 广播多实例一致性：`DspClusterConfig` / `DspReloadNotifier` / `DspReloadListener` / `DspRuntimeRefresher` |
| `generator` | `DspScriptGenerator`：按目标表结构生成 CRUD 脚本（预览 / 落库） |
| `metadata` | `DspMetadataReader`：JDBC 元数据探测（实例 / 表 / 列 / 命名转换） |
| `domain` / `mapper` | `DspScript` / `DspScriptVersion` / `DspDataSource` 及对应 Mapper（XML 在 `resources/mapper/dsp/`） |

### 接口与权限

| 前缀 | 端点 | 权限码 |
| ---- | ---- | ------ |
| `/dsp/script` | `list` / `{scriptId}` / `versions/{scriptId}` / `version/{versionId}` / `GET-POST-PUT-DELETE` CRUD / `validate` / `test` / `publish` / `rollback` / `offline` / `reload` | `dsp:script:list` `query` `add` `edit` `remove` `validate` `test` `publish` `rollback` |
| `/dsp/exec` | `POST /{slug}`、`GET /{slug}` | `dsp:execute` |
| `/dsp/datasource` | `list` / `options` / `{dataSourceId}` / CRUD / `test`(测试连接) | `dsp:datasource:list` `query` `add` `edit` `remove` `test` |
| `/dsp/generator` | `datasources` / `instances` / `tables` / `columns` / `preview` / `generate` | `dsp:generator:query` `preview` `generate` |

### 关键约定（写/调试脚本前必读）

- **运行时标识**：`namespace = 脚本 ID`，`语句 id = slug`，完整语句 id 形如 `12.order_query`；调用方只认 `slug`。
- **脚本内容**：一个脚本只写**一条** `<select|insert|update|delete>`，且**不写 `id`、不写 `namespace`、不写 `<?xml>`/DOCTYPE/`<mapper>` 包装**，全部由平台补全（`DspValidator.unwrap` 会自动剥离遗留包装并给出提示）；`<select>` 必须显式 `resultType`/`resultMap`。
- **执行结果**：`select` 返回 `rows`（每行为 Map，键为数据库**原始列名**，因 `mapUnderscoreToCamelCase=false`）；DML 返回 `affectedRows`；`test` 走事务回滚预览，不落库。
- **参数**：`#{}` 预编译（推荐）；`${}` 原样拼接，校验阶段会告警。翻页处（`LIMIT`/`OFFSET`）的占位符会被自动规整为整数以兼容 URL 查询串。
- **生效时机**：发布 / 回滚 / 换绑数据源 / 下线 / 删除均在**数据库事务提交后**（`DspAfterCommit`）同步本实例内存注册，并 `notifyPeers` 广播；回滚还还原绑定的数据源；换绑是「先在新源注册成功再卸旧源」。

### 校验能力与已知限制（别误判为「已通过 = 一定能跑」）

`DspValidator.validate(scriptId, slug, content, databaseType)` 全程**不连目标数据库**，做的是：

1. 自动补全 XML 包装后的 XML 合法性 + 基于临时 `Configuration`（复用主配置类型别名，不污染主配置）的 MyBatis 语义校验；
2. 语句数量、`resultType`/`resultMap`、残留包装、`${}` 注入告警；
3. 按目标数据源的 `dbType` 做**方言提示**（`DIALECT_RULES`：反引号、两参数 LIMIT、`sysdate()`、`ifnull()`、`nvl()`、`rownum`、`from dual`、`||`、`TOP n` 等），**只告警不阻断**，存在误判可能。

因此：**校验通过 ≠ SQL 能在目标库执行**。表名/列名拼错、方言规则的漏网写法，只有真正 `/dsp/exec` 或页面「测试」时才会暴露。改动校验逻辑时不要把它变成有副作用的连库操作而不做超时/不可达处理。

### 运行时与多实例

- `DspDataSourceManager` 按 `dataSourceId` 维护「Druid 连接池 + 独立 MyBatis `Configuration` + `SqlSessionTemplate` + 事务管理器」，首次使用懒加载并缓存；**默认库（id=0）复用 Spring 现有 `SqlSessionFactory`**，其余数据源才自建。
- 运行时注册只存在各实例 JVM 内存中，**数据库只存脚本/版本/数据源定义**。
- 多实例经 Redis 频道广播（`dsp.cluster.enabled` 默认 `true`、`dsp.cluster.reload-channel` 默认 `ruoyi:dsp:reload`；同 Redis 上的多个应用需分频道配置）；整体重载 `reloadAll` 是「**先 `unregisterAll` 再按库重建**」，库里已删除/已下线的脚本不会残留在内存。广播失败只记错误日志，其余实例会保持旧状态，此时需手动 `POST /dsp/script/reload` 或重启修正。

### 表与初始化

`sql/ry_20260417.sql` → `sql/quartz.sql` → `sql/dsp_20261013.sql`（后者建 `dsp_script`、`dsp_script_version`、`dsp_data_source` 三表，并注册菜单「动态脚本」及 `dsp:*` 权限码）。

### 测试

`mvn -pl ruoyi-dsp -am test`；测试类在 `ruoyi-dsp/src/test/java/com/ruoyi/dsp/`（12 个，H2 内存库，**不依赖 MySQL**）。改动 `engine` / `cluster` / `service` 后至少要跑通它。

## 四、代码生成：Angular 模板

- 触发方式：页面「系统工具 → 代码生成」导入表时选「前端模板类型 = Angular NG-ZORRO 模版」；后端 `tplWebType` 落到 `gen_table.tpl_web_type`，判定逻辑在 `VelocityUtils`（`ANGULAR = "angular"`，`useWebType = "vm/angular"`）。
- **后端没有默认值**：`GenController.importTableSave` / `createTableSave` 强制要求传 `tplWebType`；默认值 `angular` 由前端 `inforstack-ng` 提供（`DEFAULT_TPL_WEB_TYPE`）。
- 模板文件与产物（`vm/angular/` → 前端工程相对路径 `angular/src/app/...`，实际落地到 `inforstack-ng/src/app/`）：

| 模板 | 产物 |
| ---- | ---- |
| `api.service.ts.vm` | `api/{moduleName}/{businessName}.ts` |
| `model.ts.vm` | `core/models/{moduleName}.ts` |
| `page.ts.vm` / `page.html.vm` / `page.less.vm` | `pages/{module}/{business}/{business}.ts|.html|.less`（NG-ZORRO 表格 + 弹窗 CRUD） |

> 模板**仅支持单表 CRUD**，树表 / 主子表仍走 Vue 模板；生成的页面还需要在前端 `pages/component-map.ts` 等处登记，前端侧约定见 `inforstack-ng/AGENTS.md`。

## 五、构建与运行

```bash
# 无 mvnw 包装器，直接用 mvn
mvn clean package -Dmaven.test.skip=true     # 产物：ruoyi-admin/target/ruoyi-admin.jar
mvn -pl ruoyi-dsp -am test                   # 只测 dsp 模块
java -jar ruoyi-admin/target/ruoyi-admin.jar # 或运行 com.ruoyi.RuoYiApplication
```

- 端口 `8080`、`context-path: /`；接口文档 `/swagger-ui.html`、`/v3/api-docs`。
- 数据库默认库名为 **`ry_ng`**（区别于上游的 `ry-vue`），配置在 `ruoyi-admin/src/main/resources/application-druid.yml`；`application.yml` 中 Redis 与上传路径 `ruoyi.profile` 也需按本机环境调整（当前提交值指向内网 `192.168.2.105`）。
- 辅助脚本：`bin/{clean,package,run}.bat`；`ry.bat`（Windows 交互式 start/stop/restart/status）、`ry.sh`（Linux）。

## 六、上游同步

`RuoYi-Vue` 与本仓库无共同祖先（历史被压平）且换行符不同，**不能用 `git pull upstream`**。用 `scripts/sync-upstream.ps1` 做基于 `vendor/ruoyi-vue` 分支的三方合并：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/sync-upstream.ps1
# 可选 -Commit / -Branch master / -Upstream <url>；退出码 0 成功 / 2 冲突待人工 / 3 工作区脏
```

白名单只含 `pom.xml`、`sql/`、`ruoyi-admin/`、`ruoyi-common/`、`ruoyi-framework/`、`ruoyi-system/`、`ruoyi-quartz/`、`ruoyi-generator/` —— **`ruoyi-dsp/` 不在其中，同步不会覆盖定制模块**；前端目录永不被触碰。注意 `ruoyi-generator` 在同步范围内，Angular 模板的定制要从合并冲突中保住。

## 七、跨仓库约定（与 inforstack-ng）

- 前端 `inforstack-ng/proxy.conf.json` 全部代理到 `http://localhost:8080`，需要代理的前缀：`/dev-api`（`pathRewrite` 去掉前缀）、`/v3/api-docs`、`/profile`。**改接口路径/请求响应结构必须同步前端仓库**（跨仓库任务需在两边分别提交推送）。
- 前端开发服 `4200`（`npm start`），测试 `npm test`（Vitest）、`npm run build`、`npm run lint`。
- 涉及 NG-ZORRO 组件的工作在前端仓库，必须先查其 `docs/ng-zorro/llms.txt` 索引再按行局部读 `llms-full.txt`，禁止凭记忆编造组件 API。

## 八、开发流程与铁律（本仓库）

1. **先调研再动手**：读清相关模块现有实现、依赖与影响面，不凭猜测改代码；范围或验收标准不清时先提问。
2. **划定范围**：功能改动优先落在衍生部分（`ruoyi-dsp`、`vm/angular`、`sql/dsp_*.sql`）；从上游复制来的代码非必要不改；不做「顺手」的无关改动。
3. **不动只读对象**：`RuoYi-Vue`、`RuoYi-Vue3`、本仓库的 `ruoyi-vue3/`，一个字节都不改（含格式化）。
4. **验证后再提交**：至少跑 `mvn -pl <受影响模块> -am test` 或 `mvn clean package -Dmaven.test.skip=true`；未验证不提交。
5. **一任务一提交**：只 `git add` 本任务涉及的文件（不要 `git add .`），提交后 `git push origin main`；跨仓库任务两边分别提交推送。网络失败时用一次性代理参数（`git -c http.proxy=http://127.0.0.1:10808 ...`），不改 `git config`。
