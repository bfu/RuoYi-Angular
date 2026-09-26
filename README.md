<p align="center">
  <img alt="logo" src="https://oscimg.oschina.net/oscnet/up-d3d0a9303e11d522a06cd263f3079027715.png" width="120">
</p>
<h1 align="center" style="margin: 24px 0; font-weight: bold;">RuoYi-Angular</h1>
<h4 align="center">RuoYi-Vue 3.9.2 后端 · 内置 Angular 代码生成模板</h4>
<p align="center">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg?logo=springboot&logoColor=white">
  <img alt="JDK" src="https://img.shields.io/badge/JDK-17%2B-ED8B00.svg?logo=openjdk&logoColor=white">
  <img alt="MyBatis" src="https://img.shields.io/badge/MyBatis-4.1-C1666B.svg">
  <img alt="Redis" src="https://img.shields.io/badge/Redis-5%2B-DC382D.svg?logo=redis&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-green.svg">
</p>

> 本仓库**只含后端**与代码生成模板。配套 Angular 前端在 👉 **[bfu/inforstack-ng](https://github.com/bfu/inforstack-ng)**（Angular 22 + NG-ZORRO 22）。

## 项目简介

后端完整沿用 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 官方实现（多模块 Spring Boot 工程），**接口零改造**，因此官方 Vue 前端与 Angular 前端可以直接共用同一套后端服务。

在此之上做的定制：

- **内置 Angular 代码生成模板**：`ruoyi-generator` 支持 `tplWebType = angular`，可生成 NG-ZORRO 风格的 Angular 独立组件（`.ts` / `.html` / `.less`）与 `api.service.ts`、模型类，生成结果可直接放入 `inforstack-ng`。
- **接口保持官方兼容**：认证、菜单、字典、权限、日志、定时任务等全部接口与官方 3.9.2 一致。

## 配套仓库

| 仓库 | 说明 |
| :--- | :--- |
| [bfu/RuoYi-Angular](https://github.com/bfu/RuoYi-Angular) | 本仓库：Spring Boot 后端 + 代码生成模板 + SQL 脚本 |
| [bfu/inforstack-ng](https://github.com/bfu/inforstack-ng) | Angular 22 + NG-ZORRO 22 前端（独立仓库） |

两个仓库独立演进，只要接口不变即可任意组合；前端 `/dev-api` 通过 `proxy.conf.json` 代理到本后端 `http://localhost:8080`，无需配置跨域。

## 技术栈

| 分层 | 技术 | 说明 |
| :--- | :--- | :--- |
| 基础框架 | Spring Boot 4.1 | JDK 17+，父工程 `pom.xml` 统一管理依赖版本 |
| 安全框架 | Spring Security + JWT | 无状态登录，支持多终端接入 |
| 持久层 | MyBatis + PageHelper + Druid | Druid 连接池监控内置 |
| 数据库 | MySQL 5.7 / 8.0 | 字符集 `utf8mb4` |
| 缓存 | Redis 5+ | 登录态、字典、缓存监控 |
| 定时任务 | Quartz | `ruoyi-quartz` 模块 |
| 接口文档 | Springdoc（OpenAPI 3） | `/v3/api-docs`、`/swagger-ui.html` |

## 目录结构

```
RuoYi-Angular
├── ruoyi-admin         # 启动模块（RuoYiApplication，端口 8080）
├── ruoyi-framework     # 框架核心：安全、配置、拦截器、数据源
├── ruoyi-system        # 系统业务：用户 / 角色 / 菜单 / 部门 / 字典 ...
├── ruoyi-common        # 通用工具、注解、常量、异常处理
├── ruoyi-quartz        # 定时任务与调度日志
├── ruoyi-generator     # 代码生成（含 vm/angular 模板）
│   └── src/main/resources/vm/angular   # api.service.ts / model.ts / page.ts / page.html / page.less
├── sql                 # 初始化脚本（ry_*.sql、quartz.sql）
├── doc                 # 官方环境使用手册
└── scripts             # 辅助脚本（如 sync-upstream.ps1）
```

仓库内**不包含**官方 Vue3 前端：本地克隆的 `ruoyi-vue3/` 仅作对照参考，已被 `.gitignore` 忽略。

## 内置能力

| 分组 | 模块 |
| :--- | :--- |
| 系统管理 | 用户、部门、岗位、菜单、角色、字典、参数、通知公告 |
| 系统监控 | 在线用户、定时任务、操作日志、登录日志、缓存监控、服务监控、连接池（Druid） |
| 系统工具 | 代码生成、系统接口（Swagger）、表单构建（官方能力） |
| 基础设施 | 登录/注册、验证码、文件上传下载、数据权限、字典缓存、操作日志切面、多数据源 |

## 代码生成（Angular）

在「系统工具 → 代码生成」导入表后，把生成模板的前端类型选为 **angular**（`tplWebType`），即可产出：

| 生成文件 | 说明 |
| :--- | :--- |
| `api.service.ts` | 基于统一请求封装的接口服务 |
| `model.ts` | 实体与查询参数模型 |
| `page.ts` / `page.html` / `page.less` | NG-ZORRO 表格 + 弹窗增删改查的独立组件 |

> 当前 Angular 模板仅支持单表 CRUD（不含树表 / 主子表），树表与主子表仍使用 Vue 模板生成。

## 快速开始

### 环境要求

- JDK 17+、Maven 3.8+
- MySQL 5.7/8.0、Redis 5+

### 1. 初始化数据库

```sql
CREATE DATABASE `ry-vue` DEFAULT CHARACTER SET utf8mb4;
```

依次执行 `sql/ry_*.sql`、`sql/quartz.sql`。

### 2. 配置并启动后端

修改 `ruoyi-admin/src/main/resources/application.yml`（数据源、Redis、上传路径；Redis 在 `application-druid.yml` 中亦有相关配置），然后：

```bash
mvn clean package                        # 打包
java -jar ruoyi-admin/target/ruoyi-admin.jar
# 或直接运行 ruoyi-admin 模块下的 RuoYiApplication
```

后端默认监听 `http://localhost:8080`，接口文档：`http://localhost:8080/swagger-ui.html`。

### 3. 启动前端

```bash
git clone https://github.com/bfu/inforstack-ng.git
cd inforstack-ng
npm install
npm start          # http://localhost:4200
```

### 默认账号

`admin / admin123`

## 与官方 RuoYi-Vue 的差异

| 维度 | 官方 RuoYi-Vue | 本项目 |
| :--- | :--- | :--- |
| 后端 | Spring Boot 2/3 | Spring Boot 4.1（接口未改动） |
| 前端 | Vue 2 / Vue 3 + Element | Angular 22 + NG-ZORRO（独立仓库 `inforstack-ng`） |
| 代码生成 | Vue 模板 | 新增 Angular 模板（单表 CRUD） |
| 仓库组织 | 前后端同仓 | 前后端分仓，接口契约保持一致 |

## 路线图

- [x] 后端升级至 Spring Boot 4.1，接口保持官方兼容
- [x] Angular（NG-ZORRO）代码生成模板：单表 CRUD
- [ ] Angular 模板支持树表与主子表
- [ ] 补充 Docker Compose 一键启动（MySQL + Redis + 后端）
- [ ] CI：后端 `mvn verify` 自动构建

## 开源协议

本项目基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 二次开发，遵循原项目的 **MIT License**，详情见 [LICENSE](./LICENSE)。

使用本项目请保留若依的版权声明与作者信息。

## 致谢

- [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)：后端与整体设计全部来源于若依开源项目。
- [NG-ZORRO](https://ng.ant.design/)、[Angular](https://angular.dev/)：前端实现基础。

如果这个项目对你有帮助，欢迎点个 Star ⭐，也欢迎提交 Issue 与 PR。
