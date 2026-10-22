# ADR: 状态存储收敛进 MySQL 表，Redis 降级为可插拔备选

中文 | [English](../en/decisions/why-redis-optional.md)

> 状态：已采纳
> 日期：2026-10-15
> 决策者：架构团队

---

## 背景

短生命周期状态（短信验证码、令牌吊销黑名单、设备握手材料、第三方 access_token）最初由 Redis 承载，业务数据在 MySQL——生产环境必须同时运维**两个**必需中间件。

收敛前先对这些状态做了特征分析，它们有三个共同点：

1. **短 TTL**：验证码 60s、限频计数 24h、吊销黑名单为令牌剩余有效期、握手材料约 24h+5min——全部在分钟到一天的量级；
2. **键级原子性即可**：只需 `SETNX` / `INCR` 这一级语义，没有 Hash、List、Stream 等复杂结构需求；
3. **数据可再生**：验证码可重发、黑名单丢一条只影响单次登出窗口、握手材料可重新协商——全部不是不可恢复的权威数据。

再看访问模式：全部是**主键点查/点写**，没有范围扫描。按 60 万注册用户的口径推算，峰值约数百 QPS——这个量级对单表主键点查而言，4C16G 云 MySQL 有 15~50 倍余量。

于是问题变成：**一个 MySQL 单表就能承接的负载，值不值得为它常驻一个 Redis？**

## 决策

短生命周期状态**收敛进 MySQL 一张 `app_state` 表**；同时引入 `StateStore` 接口抽象，提供两个实现：

- `MysqlStateStore`（**默认**，`app.state.store=mysql` 或未配置时生效）——零额外中间件；
- `RedisStateStore`（**预留回切**，`app.state.store=redis` 时生效）——改配置即可启用，**业务代码零改动**。

生产中间件由 2 个减为 1 个。Redis 从"必选依赖"降级为"可插拔备选"。

### 表结构（全部时间列为秒级 UNIX 时间戳）

```sql
CREATE TABLE IF NOT EXISTS `app_state` (
  `state_key`   VARCHAR(128) NOT NULL             COMMENT '状态键',
  `state_value` TEXT         NOT NULL             COMMENT '状态值（JSON 序列化）',
  `expire_time` BIGINT       NOT NULL             COMMENT '过期时间（秒级 UNIX 时间戳）',
  `update_time` BIGINT       NOT NULL DEFAULT 0   COMMENT '最近写入时间（排障用）',
  PRIMARY KEY (`state_key`),
  KEY `idx_app_state_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 接口：六个原语

```java
public interface StateStore {
    void set(String key, Object value, Duration ttl);
    <T> T get(String key, Class<T> type);          // 不存在/过期/值损坏 → null
    boolean setIfAbsent(String key, Object value, Duration ttl);
    void delete(String key);
    boolean exists(String key);
    long increment(String key, Duration ttlOnCreate); // 原子自增，首建时设 TTL
}
```

### 装配：条件注解完成切换

```java
@Component
@ConditionalOnProperty(name = "app.state.store", havingValue = "mysql", matchIfMissing = true)
public class MysqlStateStore implements StateStore { ... }

@Component
@ConditionalOnProperty(name = "app.state.store", havingValue = "redis")
public class RedisStateStore implements StateStore { ... }
```

`matchIfMissing = true` 保证不配置时默认走 MySQL——**运维的默认路径就是最少组件的路径**。

## 考虑的替代方案

### 方案 A：维持 Redis 常驻

| 维度 | Redis 常驻 | 收敛进 MySQL |
|---|---|---|
| 必需中间件 | 2 个（MySQL + Redis） | 1 个 |
| 运维面 | 备份、监控、故障域 ×2 | ×1 |
| TTL 原生支持 | 原生 | 应用层判过期 + 定时清理 |
| 原子自增 | `INCR` 原生 | `ON DUPLICATE KEY UPDATE` 单语句 |
| 容量论证 | 不需要 | 需要（已做：峰值数百 QPS，余量 15~50 倍） |
| 回切成本 | — | 零代码（仅配置） |

### 方案 B：彻底删除 Redis 代码

比决策更"干净"——不保留 `RedisStateStore`，pom 也移除 `spring-boot-starter-data-redis`。

**否决理由**：回切的代价从"改一行配置"上升为"加依赖 + 补实现 + 全量回归"。而收敛决策本身带有一个未验证假设（MySQL 承接峰值负载），保留实现就是给这个假设留了一条**已测试的逃生通道**——成本是 78 行常驻代码与一个 test scope 之外的可选依赖。

## 选择可插拔的理由

1. **`app_state` 的原子语义靠刻意设计的 SQL 保持可见**。`setIfAbsent` = 先清逻辑过期的旧行再 `INSERT IGNORE`（主键冲突 = 键仍活跃，天然原子）；`increment` = 单语句 `ON DUPLICATE KEY UPDATE` 行锁自增，语义对齐 Redis `INCR`：

   ```sql
   INSERT INTO app_state (state_key, state_value, expire_time, update_time)
   VALUES (#{key}, '1', #{expireTime}, #{now})
   ON DUPLICATE KEY UPDATE
     state_value = IF(expire_time <= UNIX_TIMESTAMP(), '1', CAST(state_value AS SIGNED) + 1),
     expire_time = IF(expire_time <= UNIX_TIMESTAMP(), VALUES(expire_time), expire_time)
   ```

   这与[为什么选 MyBatis](why-mybatis-not-jpa.md) 是同一纪律：**并发安全必须能被指着某条 SQL 问清楚**，而不是藏在框架的 flush 时机里。

2. **过期是"读路径过滤 + 定时清理"双保险，不依赖单一机制**。读路径 `WHERE expire_time > UNIX_TIMESTAMP()` 保证过期键视同不存在；后台清理任务每 10 分钟按 5000/批、最多 20 批物理删除。即使清理任务停摆，语义仍正确——只是表会胖。

3. **切换开关是配置而非代码**，这决定了回切在故障场景下可用。生产出问题的深夜，"改 `application.yml` 一行 + 滚动重启"与"写代码、编译、回归、发布"是两种完全不同量级的处置动作。

4. **一张刻意不给实体类的 KV 表**。`app_state` 走原生 SQL Mapper，不做实体映射——避免为纯 KV 结构引入无业务语义的实体，也避免它被误当作业务表加字段。

## Trade-off

### 付出的代价

- **TTL 不再是中间件原生能力**。Redis 里 TTL 由内存管理器自动回收；MySQL 里过期行要靠读路径过滤 + 定时任务清理，清理停摆则表膨胀。
  - 缓解：读路径过滤保证语义正确性与清理解耦；清理任务有批量上限防止大事务；`idx_expire_time` 保证清理与过滤都是索引操作。

- **两个实现的时钟域不同**。MySQL 实现写 `expire_time` 用应用时钟、判过期用数据库 `UNIX_TIMESTAMP()`；Redis 实现用 Redis 自己的钟。跨实现切换时过期边界可能有偏移；应用与数据库时钟漂移时，MySQL 实现内部也不自洽。
  - 缓解：契约中明确"时钟归属"（统一改用数据库时钟写入是候选修法）；补一份**双实现共用的契约测试**锁定六原语语义——这是当前最该补的一项。

- **`increment` 对曾被 `set` 写入非整数的键会得到 0**（`CAST(... AS SIGNED)` 遇 JSON 字符串得 0）。这是契约缺口而非实现 bug——"计数键"与"值键"的键空间没有强制分离。
  - 缓解：契约规定 `increment` 的键不得经 `set` 写入非整数值；用键前缀区分计数键与值键；契约测试覆盖此交叉场景。

- **单实例假设**。清理任务无分布式锁——单实例下正确，横向扩容会导致重复清理（无害但浪费）与写放大。
  - 缓解：当前单实例是硬约束而非当前状态，文档已标注；多实例化前须先给清理任务加锁或改为按实例分片。

### 收益

- **生产中间件 2 → 1**：备份、监控、升级、故障域同步收敛——对小团队这是每天都在兑现的收益；
- **回切零代码**：`RedisStateStore` 常驻且随条件注解就绪，逃生通道始终可用；
- **容量决策有据可依**：收敛不是"我觉得 Redis 没用"，而是先完成访问模式分析与余量推算再下的结论。

## 后续演进

1. **契约测试（最优先）**：补一份对两个实现跑同一断言集的参数化测试，锁定六原语语义——尤其过期边界、`setIfAbsent` 与并发 `increment` 的交叉场景；
2. **时钟归属收敛**：评估 MySQL 实现统一改用数据库时钟写入，消除实现内部的时钟混用；
3. **Redis 的重新引入条件**：当出现**非键级原子**的状态需求（如会话粘性的 Hash 结构、跨键事务、真正的发布订阅），或实测 MySQL 点查 P99 超出预算——届时 `app.state.store=redis` 是第一动作，而非重写。

---

相关：[为什么选 MyBatis 而非 JPA](why-mybatis-not-jpa.md) · [数据权限模型](../architecture/data-permission-model.md)
