# AGENTS.md

RuoYi-Vue 的多工程仓库：Spring Boot 后端 + 多个并行前端。

## 子工程

| 目录 | 说明 |
| ---- | ---- |
| `ruoyi-admin`、`ruoyi-framework`、`ruoyi-system`、`ruoyi-common`、`ruoyi-quartz`、`ruoyi-generator` | Spring Boot 后端（Maven 多模块，父 `pom.xml`） |
| `ruoyi-vue3` | Vue 3 + Element Plus 前端，**本地跟踪 `yangzongzhuan/RuoYi-Vue3` 的克隆目录，已被 `.gitignore` 忽略、不入库**，仅作 Angular 改写时的对照参考，不要改动其中的代码 |
| `inforstack-ng`（仓库外） | Angular 22 + NG-ZORRO 22 前端，**独立仓库 `bfu/inforstack-ng`，位于本仓库同级的 `../inforstack-ng`**，规则与离线组件文档见其 `AGENTS.md` |

## 通用约定

- 动手前先读目标子工程的 `AGENTS.md` / `README.md`，不要跨子工程做无关改动。
- 上游 vue3 有更新时把它同步到最新用于对照，功能改动一律落在 `inforstack-ng`；涉及 NG-ZORRO 组件的开发，必须查 `inforstack-ng/docs/ng-zorro/llms.txt` 索引后再按行局部读取 `llms-full.txt`，不得整文件读入上下文，也不得凭记忆编造组件 API。
