<p align="center">
  <img alt="logo" src="https://oscimg.oschina.net/oscnet/up-d3d0a9303e11d522a06cd263f3079027715.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">RuoYi-Angular v3.9.2</h1>
<h4 align="center">基于 Spring Boot + Angular 前后端分离的 Java 快速开发框架</h4>
<p align="center">
  <img alt="Angular" src="https://img.shields.io/badge/Angular-22-dd0031.svg?logo=angular&logoColor=white">
  <img alt="NG-ZORRO" src="https://img.shields.io/badge/NG--ZORRO-22-1890ff.svg">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1-6db33f.svg?logo=springboot&logoColor=white">
  <img alt="JDK" src="https://img.shields.io/badge/JDK-17%2B-orange.svg?logo=openjdk&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-green.svg">
</p>

## 平台简介

本项目在 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 官方后端基础上，**将前端由 Vue 完整重写为 Angular**：后端沿用 Spring Boot 多模块工程（`ruoyi-admin`/`ruoyi-framework`/`ruoyi-system`/`ruoyi-common`/`ruoyi-quartz`/`ruoyi-generator`），前端为独立 Angular 工程 `inforstack-ng`（Angular 22 独立组件 + NG-ZORRO 22 + RxJS），沿用若依的菜单、权限、字典、代码生成等全部能力，页面布局与交互与官方 Vue 版保持一致。

- 后端：Spring Boot 4.1、Spring Security、Redis、JWT（与官方 RuoYi-Vue 3.9.2 完全兼容，接口零改动）。
- 前端：Angular 22（standalone 独立组件，无 NgModule）、NG-ZORRO 22、RxJS 7、ECharts 6、Less，构建工具为 Angular CLI / esbuild。
- 认证：JWT Token，支持多终端接入；动态路由由后端菜单驱动，按钮级权限由指令 `*hasPermi` 控制。
- 工程内仍保留官方 Vue3 前端目录 `ruoyi-vue3`，仅作对照参考，非本项目主前端：该目录是 `yangzongzhuan/RuoYi-Vue3` 的本地克隆，已被 `.gitignore` 忽略，不随本仓库提交。

## 技术栈

| 分层 | 技术 | 说明 |
| :--- | :--- | :--- |
| 前端框架 | Angular 22 | 全量独立组件（standalone），应用级 providers 集中在 `src/app/app.config.ts` |
| UI 组件库 | NG-ZORRO 22 | Ant Design 的 Angular 实现，中文语言包 + 图标按需注册 |
| 状态/数据流 | RxJS 7 + Service 单例 | 用户信息、字典等轻量状态放在 `src/app/store` |
| 路由 | Angular Router | 静态路由 + 后端菜单动态路由（`pages/dynamic-routes.ts`） |
| 图表 | ECharts 6 | 封装为 `shared/components/echart` 组件 |
| 后端框架 | Spring Boot 4.1 + Spring Security | JDK 17+ |
| 持久化 | MyBatis + PageHelper + Druid | MySQL 为主，Druid 连接池监控内置 |
| 缓存 | Redis | 登录态、字典、缓存监控 |
| 接口文档 | Springdoc / Swagger | `/v3/api-docs`、`/swagger-ui.html` |

## 目录结构

```
RuoYi-Vue
├── ruoyi-admin         # 后端启动模块（Spring Boot 入口、端口 8080）
├── ruoyi-framework     # 框架核心（安全、配置、拦截器、数据源）
├── ruoyi-system        # 系统业务模块（用户/角色/菜单/字典...）
├── ruoyi-common        # 通用工具与注解
├── ruoyi-quartz        # 定时任务模块
├── ruoyi-generator     # 代码生成模块
├── inforstack-ng       # ★ Angular 前端工程（本项目主前端）
│   ├── src/app/api     # 后端接口封装（system / monitor / tool）
│   ├── src/app/core    # 请求服务、拦截器、下载、路由复用策略
│   ├── src/app/layout  # 布局：侧边栏、导航、标签页、主题设置
│   ├── src/app/pages   # 业务页面：system / monitor / tool / login / ...
│   ├── src/app/shared  # 指令、守卫、管道、通用组件
│   └── proxy.conf.json # 开发代理：/dev-api → http://localhost:8080
├── ruoyi-vue3          # 官方 Vue3 前端（本地克隆作为对照参考，已 gitignore，不提交）
└── sql                 # 初始化脚本（ry_*.sql、quartz.sql）
```

## 已实现功能

### 系统管理

| 模块 | 页面路径（前端目录） | 说明 |
| :--- | :--- | :--- |
| 用户管理 | `pages/system/user` | 用户配置、角色分配、重置密码、导入导出 |
| 部门管理 | `pages/system/dept` | 组织机构树、数据权限 |
| 岗位管理 | `pages/system/post` | 职务配置 |
| 菜单管理 | `pages/system/menu` | 菜单/按钮权限标识，驱动前端动态路由 |
| 角色管理 | `pages/system/role` | 菜单权限分配、数据范围划分 |
| 字典管理 | `pages/system/dict` | 字典类型与字典数据维护 |
| 参数设置 | `pages/system/config` | 系统动态参数 |
| 通知公告 | `pages/system/notice` | 公告发布与维护 |

### 系统监控

| 模块 | 页面路径 | 说明 |
| :--- | :--- | :--- |
| 在线用户 | `pages/monitor/online` | 活跃用户监控、强制下线 |
| 定时任务 | `pages/monitor/job` | 任务调度与执行日志 |
| 操作日志 | `pages/monitor/operlog` | 正常/异常操作记录 |
| 登录日志 | `pages/monitor/logininfor` | 登录记录与异常 |
| 缓存监控 | `pages/monitor/cache` | 缓存查询、命令统计 |
| 服务监控 | `pages/monitor/server` | CPU、内存、磁盘、堆栈 |
| 连接池监视 | `pages/monitor/druid` | Druid 连接池与 SQL 分析（iframe 内嵌） |

### 系统工具

| 模块 | 页面路径 | 说明 |
| :--- | :--- | :--- |
| 代码生成 | `pages/tool/gen` | 导入表、编辑字段、预览与生成下载 |
| 系统接口 | `pages/tool/swagger` | Swagger 文档内嵌 |

### 通用能力

登录/注册、验证码、404/401 错误页、个人中心、主题与布局设置（侧边栏主题、标签页、固定头部）、多标签页 + 路由复用（`core/reuse-strategy.ts`）、文件上传/图片上传组件、字典标签与字典管道、按钮级权限指令。

## 快速开始

### 环境要求

- JDK 17+、Maven 3.8+
- MySQL 5.7/8.0、Redis 5+
- Node.js 20.19+ / 22+（Angular 22 要求）、npm 10+

### 1. 初始化数据库

1. 创建数据库 `ry-vue`（字符集 `utf8mb4`）。
2. 依次执行 `sql/ry_*.sql`、`sql/quartz.sql`。

### 2. 启动后端

修改 `ruoyi-admin/src/main/resources/application.yml` 中的数据库与 Redis 连接信息（Redis 配置在 `application-druid.yml`），然后：

```bash
mvn clean package            # 打包
java -jar ruoyi-admin/target/ruoyi-admin.jar
# 或直接运行 ruoyi-admin 模块下的 RuoYiApplication
```

后端默认监听 `http://localhost:8080`。

### 3. 启动 Angular 前端

```bash
cd inforstack-ng
npm install
npm start          # ng serve，默认 http://localhost:4200
```

`/dev-api` 请求由 `proxy.conf.json` 反向代理到 `http://localhost:8080`，无需额外配置跨域。

其他常用命令：

```bash
npm run build      # 生产构建，产物在 dist/
npm test           # Vitest 单元测试
```

### 默认账号

`admin / admin123`

## 开发约定

- 全部使用**独立组件**：`@Component({ imports: [...] })`，不新建 NgModule；跨应用级 provider 统一在 `src/app/app.config.ts` 注册。
- 组件模板与样式独立成文件：`templateUrl: './xxx.html'` + `styleUrl: './xxx.less'`（Less）。
- `NzModalService` 已在 `app.config.ts` 通过 `importProvidersFrom(NzModalModule)` 注入根注入器，**不要删除或重复 provide**（拦截器依赖它）。
- 编写 UI 前先查离线组件文档：`docs/ng-zorro/llms.txt` 定位组件起始行 → 按行区间读取 `docs/ng-zorro/llms-full.txt`，**禁止整文件读入上下文、禁止凭记忆编造 NG-ZORRO API**。

## 与官方 RuoYi-Vue 的差异

| 维度 | 官方 RuoYi-Vue | 本项目 |
| :--- | :--- | :--- |
| 前端框架 | Vue 2 / Vue 3 | Angular 22（standalone） |
| UI 库 | Element UI / Element Plus | NG-ZORRO 22 |
| 构建工具 | Vite / Vue CLI | Angular CLI（esbuild） |
| 状态管理 | Vuex / Pinia | RxJS Service 单例 |
| 权限控制 | `v-hasPermi` 指令 | `*hasPermi` 结构型指令 |
| 后端 | Spring Boot 2/3/4 | Spring Boot 4.1（接口未改动） |

后端接口完全兼容官方版本，因此官方 Vue 前端与本项目 Angular 前端可共用同一套后端服务。

## 路线图

- [x] 登录/注册、动态菜单路由、按钮级权限
- [x] 系统管理 8 个模块、系统监控 7 个模块、系统工具 2 个模块
- [ ] 表单构建器（在线构建器）
- [ ] 案例演示页面（多 tab 组件示例）
- [ ] 前端单元测试覆盖率补齐
- [ ] 补充 Angular 版界面截图

## 开源协议

本项目基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 二次开发，遵循原项目的 **MIT License**，详情见 [LICENSE](./LICENSE)。

使用本项目请保留若依的版权声明与作者信息；若用于商业项目，请自行确认第三方依赖（NG-ZORRO、ECharts 等）的许可要求。

## 致谢

- [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)：后端与整体设计全部来源于若依开源项目。
- [NG-ZORRO](https://ng.ant.design/)：Ant Design 的 Angular 实现。
- [Angular](https://angular.dev/)、[ECharts](https://echarts.apache.org/)。

如果这个项目对你有帮助，欢迎点个 Star ⭐，也欢迎提交 Issue 与 PR 一起完善。
