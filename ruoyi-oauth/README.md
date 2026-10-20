# ruoyi-oauth — API Key 免登录授权

给后端加一层 **API Key 免登录**能力：调用方在请求头带上密钥即可访问接口，无需先登录换 JWT；
密钥绑定到某个系统用户，认证后完全以该用户身份行事（权限与登录态一致）。

## 快速开始

```sql
-- 1. 建表 + 菜单权限（前置：sql/ry_*.sql / quartz.sql / dsp_*.sql 已执行）
source sql/oauth_20261021.sql;

-- 2. 启动后在「开放接口 → 密钥管理」创建密钥，明文只展示一次
```

```bash
# 调用方：带请求头即可，无需 Authorization/JWT
curl -H "X-API-Key: rak_xxxx" http://localhost:8080/dsp/exec/order_query
```

```yaml
# application.yml（默认值如下，可不配）
ruoyi:
  oauth:
    enabled: true        # false 后密钥请求头被忽略，全部走登录/JWT 流程
    header-name: X-API-Key
```

## 工作原理

1. `ApiKeyAuthenticationFilter`（OncePerRequestFilter）从请求头取密钥：
   - **没有请求头** → 直接放行，后续流程与改造前完全一致（JWT 过滤器处理）；
   - **有请求头** → 调 `IOauthApiKeyService.authenticate(apiKey)`。
2. 认证按 `SHA-256(明文密钥)` 摘要精确查库（库里不存明文，无法反查），依次校验：
   密钥存在 → 未停用 → 未过期 → 绑定用户存在且未删除/未停用。
3. 通过则构造绑定用户的 `LoginUser`（`userId/deptId/user/permissions`）写入
   `SecurityContext`，等价于该用户已登录；后续 `@PreAuthorize("@ss.hasPermi(...)")`、
   数据权限、`@Log` 操作日志全部按该用户生效。失败返回
   `{"code":401,"msg":"API Key 无效、已停用或已过期"}`（与框架认证失败同款渲染约定）。
4. JWT 过滤器检测到已认证会自动跳过，两者互不干扰。

### 免登录接入方式（不改 framework）

`ApiKeyAuthCustomizer` 注册一个 `Customizer<HttpSecurity>` Bean。
Spring Security 会把容器内所有 `Customizer<HttpSecurity>` 应用到每条安全过滤器链，
借此把认证过滤器插到原有 JWT 过滤器之前——**`ruoyi-framework` 的 `SecurityConfig` 零改动**。

`ruoyi.oauth.enabled=false` 时该配置不装配，密钥请求头被忽略。

## 管理接口

前缀 `/oauth/apikey`，权限码与菜单见 `sql/oauth_20261021.sql`（目录 6「开放接口」→ 密钥管理 140）：

| 端点 | 方法 | 权限码 | 说明 |
| ---- | ---- | ------ | ---- |
| `/list` | GET | `oauth:apikey:list` | 分页查询（不含摘要） |
| `/{keyId}` | GET | `oauth:apikey:query` | 详情 |
| `` | POST | `oauth:apikey:add` | 新增，响应 `apiKey` 为明文（仅此一次） |
| `` | PUT | `oauth:apikey:edit` | 改名称/绑定用户/状态/过期时间（为空=永久） |
| `/reset/{keyId}` | POST | `oauth:apikey:reset` | 重置密钥（旧密钥立即失效，返回新明文） |
| `/{keyIds}` | DELETE | `oauth:apikey:remove` | 批量删除 |

## 表结构 `oauth_api_key`

| 列 | 说明 |
| -- | ---- |
| `key_id` | 主键 |
| `name` | 密钥名称（调用方备注） |
| `key_hash` | `SHA-256(明文密钥)` 十六进制，唯一索引，认证按此查找 |
| `user_id` | 绑定的用户ID（免登录即代表该用户） |
| `status` | `0` 正常 / `1` 停用 |
| `expires_time` | 过期时间，为空永久有效 |
| `last_used_time` | 最近使用时间（60 秒内节流更新，失败不影响认证） |

## 安全设计

- 明文只在创建/重置响应里出现一次，库里只有摘要，泄露库也拿不到可用密钥；
- 停用/过期/删除即时生效（下次请求即拒绝），无需重启；
- 绑定用户被停用或删除后，其名下密钥全部失效；
- 密钥与 JWT 可同时携带：API Key 优先生效，密钥无效即拒绝（即使同时携带有效 JWT）；
  未携带密钥的请求行为与改造前完全一致。

## 测试

```bash
mvn -pl ruoyi-oauth -am test   # 18 个用例：过滤器 6 + 服务 12（Mockito，不连库）
```
