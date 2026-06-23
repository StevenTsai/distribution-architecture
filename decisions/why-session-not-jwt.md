# ADR: 为什么选择 Session Token 而非 JWT

中文 | [English](../en/decisions/why-session-not-jwt.md)

> 状态：已采纳
> 日期：项目初始设计阶段
> 决策者：架构团队

---

## 背景

系统需要一种认证机制来识别"谁在操作"。主流方案有两种：

1. **Session Token**：服务端生成随机 token，存入数据库，请求时查库验证
2. **JWT (JSON Web Token)**：服务端签名生成自包含 token，请求时本地验证

本项目选择了 Session Token 方案。

## 决策

使用基于数据库的 Session Token 认证，token 存储在 `user_login_session` 表中。

### 认证流程

```
客户端                          服务端
  │                               │
  │  POST /login                  │
  │  {username, password}         │
  │ ─────────────────────────────►│
  │                               │ 1. 验证用户名密码
  │                               │ 2. 生成随机 token
  │                               │ 3. INSERT INTO user_login_session
  │  {token}                      │    (skey=token, biz=..., expire_time=...)
  │ ◄─────────────────────────────│
  │                               │
  │  GET /api/manage/xxx          │
  │  Authorization: {token}       │
  │  x-biz: medical-chaperon      │
  │ ─────────────────────────────►│
  │                               │ 4. SELECT FROM user_login_session
  │                               │    WHERE skey=? AND expire_time > NOW()
  │                               │ 5. 解析 admin_user_id → user_id
  │                               │ 6. ThreadLocal.set(userId)
  │  {data}                       │
  │ ◄─────────────────────────────│
```

### 核心代码

```java
// (source file)
private UserLoginSessionEntity getActiveBackendSession(String biz, String token) {
    UserLoginSessionEntityExample example = new UserLoginSessionEntityExample();
    example.createCriteria()
        .andSkeyEqualTo(token)
        .andBizEqualTo(biz)
        .andExpireTimeGreaterThan(new Date());  // 未过期
    List<UserLoginSessionEntity> sessions = userLoginSessionDao.selectByExample(example);
    return sessions == null || sessions.isEmpty() ? null : sessions.get(0);
}
```

## 考虑的替代方案

### 方案 A：JWT

| 优点 | 缺点 |
|------|------|
| 无状态，不需查库 | 无法主动失效（用户修改密码后旧 token 仍有效） |
| 减少数据库压力 | Token 体积大（~300 字节 vs Session 的 ~32 字节） |
| 跨服务天然支持 | 需要管理签名密钥的轮换 |
| 行业趋势 | 黑名单机制反而引入了"伪状态" |

### 方案 B：Redis Session

| 优点 | 缺点 |
|------|------|
| 查询性能高于数据库 | 引入 Redis 依赖 |
| 支持 TTL 自动过期 | 需要处理 Redis 和数据库的一致性 |
| | 增加运维复杂度 |

## 选择 Session Token 的理由

### 1. 业务场景决定

这是一个**管理后台系统**，不是面向亿级用户的 C 端应用：
- 并发用户数：几十到几百（而非百万级）
- 认证请求频率：每个 API 调用一次（而非每秒数千次）
- 数据库查询的开销完全可接受

### 2. 主动失效是刚需

管理后台场景中，以下操作需要**立即**使旧 token 失效：
- 用户修改密码
- 管理员禁用某个账号
- 安全事件后强制登出

Session Token 方案下，删除 `user_login_session` 表中的记录即可。JWT 方案需要引入黑名单机制，复杂度更高。

### 3. 多租户/多业务线支持

通过 `biz` 字段区分不同的登录来源：

```sql
-- 同一个用户可以有多个 session（管理端 + 合作方门户）
SELECT * FROM user_login_session
WHERE skey = ? AND biz = 'medical-chaperon' AND expire_time > NOW();
```

JWT 的 `audience` (aud) 字段虽然也能做类似的事情，但不够灵活。

### 4. 与现有系统兼容

本项目从 `medical-chaperon-server` 提取，该系统已经使用 Session Token 认证。保持一致可以：
- 共享同一套登录逻辑
- 无需客户端改造
- 降低迁移风险

## Trade-off

### 付出的代价

1. **每次认证需要一次数据库查询**：对高并发场景有性能影响
   - 缓解：`user_login_session` 表的 `skey` + `biz` + `expire_time` 有联合索引
   - 缓解：管理后台的并发量远低于数据库的查询能力上限

2. **需要管理 Session 过期清理**：过期的 session 记录需要定期清理
   - 缓解：可以加定时任务 `DELETE FROM user_login_session WHERE expire_time < NOW()`

3. **水平扩展时需要共享 Session 存储**：多实例部署时所有实例需要访问同一个数据库
   - 缓解：本项目使用同一个 MySQL 数据库，天然共享

### 收益

1. 实现简单，无需引入额外组件
2. 支持主动失效，满足安全合规要求
3. 与现有系统兼容，零迁移成本
4. 调试方便（session 记录在数据库中，可直接查询）

## 后续演进

如果未来需要支持更高并发或微服务间的认证共享，可以：

1. **引入 Redis 缓存 Session**：在数据库基础上加一层 Redis 缓存，TTL 与数据库过期时间一致
2. **迁移到 JWT + 黑名单**：如果需要无状态认证，可以用 JWT 但保留 Redis 黑名单支持主动失效
3. **接入 OAuth2**：如果需要对接外部系统，可以在 Session 基础上增加 OAuth2 层

---

