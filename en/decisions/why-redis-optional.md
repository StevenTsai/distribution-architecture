# ADR: Converge State Storage into a MySQL Table; Demote Redis to an Optional Pluggable

[中文](../../decisions/why-redis-optional.md) | English

> Status: Accepted
> Date: 2026-10-15
> Decision Maker: Architecture Team

---

## Background

Short-lived state (SMS verification codes, token revocation denylists, device handshake material, third-party access tokens) was originally hosted on Redis while business data lived in MySQL—meaning production had to operate **two** mandatory middleware components.

Before converging, we analyzed the characteristics of this state. Three properties in common:

1. **Short TTLs**: verification codes 60s, rate-limit counters 24h, denylist entries for the token's remaining lifetime, handshake material ~24h+5min—all in the minutes-to-a-day range;
2. **Key-level atomicity is enough**: only `SETNX` / `INCR`-class semantics are needed—no Hash, List, or Stream structures;
3. **Data is regenerable**: codes can be resent, dropping one denylist entry affects a single logout window, handshake material can be renegotiated—none of it is authoritative, unrecoverable data.

Then the access pattern: every operation is a **primary-key point lookup / point write**—no range scans. Extrapolated to 600k registered users, peak load is a few hundred QPS. At that volume, point lookups on a single table leave 15–50× headroom on a 4C16G cloud MySQL instance.

So the question became: **is it worth keeping a Redis resident for a load a single MySQL table can absorb?**

## Decision

Converge short-lived state into **one MySQL table, `app_state`**, and introduce a `StateStore` interface with two implementations:

- `MysqlStateStore` (**default**; active when `app.state.store=mysql` or unset)—zero extra middleware;
- `RedisStateStore` (**rollback path reserved**; active when `app.state.store=redis`)—enabled by changing configuration only, with **zero business-code changes**.

Mandatory production middleware drops from 2 to 1. Redis is demoted from "required dependency" to "optional pluggable."

### Table structure (all time columns are second-granularity UNIX timestamps)

```sql
CREATE TABLE IF NOT EXISTS `app_state` (
  `state_key`   VARCHAR(128) NOT NULL             COMMENT 'State key',
  `state_value` TEXT         NOT NULL             COMMENT 'State value (JSON-serialized)',
  `expire_time` BIGINT       NOT NULL             COMMENT 'Expiry (UNIX timestamp, seconds)',
  `update_time` BIGINT       NOT NULL DEFAULT 0   COMMENT 'Last write time (for debugging)',
  PRIMARY KEY (`state_key`),
  KEY `idx_app_state_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Interface: six primitives

```java
public interface StateStore {
    void set(String key, Object value, Duration ttl);
    <T> T get(String key, Class<T> type);          // missing/expired/corrupt → null
    boolean setIfAbsent(String key, Object value, Duration ttl);
    void delete(String key);
    boolean exists(String key);
    long increment(String key, Duration ttlOnCreate); // atomic increment; TTL set on first create
}
```

### Wiring: the switch is a conditional annotation

```java
@Component
@ConditionalOnProperty(name = "app.state.store", havingValue = "mysql", matchIfMissing = true)
public class MysqlStateStore implements StateStore { ... }

@Component
@ConditionalOnProperty(name = "app.state.store", havingValue = "redis")
public class RedisStateStore implements StateStore { ... }
```

`matchIfMissing = true` guarantees MySQL when nothing is configured—**the operational default is the path with fewest components**.

## Alternatives Considered

### Option A: Keep Redis mandatory

| Dimension | Redis mandatory | Converged into MySQL |
|-----------|-----------------|----------------------|
| Mandatory middleware | 2 (MySQL + Redis) | 1 |
| Operations surface | Backup, monitoring, failure domain ×2 | ×1 |
| Native TTL support | Yes | App-level expiry filter + scheduled cleanup |
| Atomic increment | Native `INCR` | Single-statement `ON DUPLICATE KEY UPDATE` |
| Capacity justification | Not needed | Required (done: few hundred QPS peak, 15–50× headroom) |
| Rollback cost | — | Zero code (config only) |

### Option B: Delete the Redis code entirely

Cleaner than the decision—drop `RedisStateStore` and remove `spring-boot-starter-data-redis` from the POM.

**Rejected**: rollback cost rises from "change one config line" to "add dependency + write implementation + full regression." The convergence decision itself rests on one unverified assumption (MySQL absorbs peak load); keeping the implementation preserves a **tested escape hatch** for that assumption—at the cost of 78 lines of resident code and an optional dependency that never reaches production scope.

## Why Pluggable

1. **The atomic semantics of `app_state` live in deliberately visible SQL**. `setIfAbsent` = delete the logically-expired row, then `INSERT IGNORE` (primary-key conflict = key still live, naturally atomic); `increment` = single-statement row-lock increment via `ON DUPLICATE KEY UPDATE`, semantics aligned with Redis `INCR`:

   ```sql
   INSERT INTO app_state (state_key, state_value, expire_time, update_time)
   VALUES (#{key}, '1', #{expireTime}, #{now})
   ON DUPLICATE KEY UPDATE
     state_value = IF(expire_time <= UNIX_TIMESTAMP(), '1', CAST(state_value AS SIGNED) + 1),
     expire_time = IF(expire_time <= UNIX_TIMESTAMP(), VALUES(expire_time), expire_time)
   ```

   This is the same discipline as [Why MyBatis over JPA](why-mybatis-not-jpa.md): **concurrency safety must be answerable by pointing at a specific SQL statement**, not hidden in a framework's flush timing.

2. **Expiry is a double belt—read-path filter plus scheduled cleanup—never a single mechanism**. The read path (`WHERE expire_time > UNIX_TIMESTAMP()`) makes expired keys invisible regardless of cleanup; a background job physically deletes them every 10 minutes in batches of 5000, capped at 20 batches. If the job stalls, semantics stay correct—the table just grows.

3. **The switch is configuration, not code**—which is what makes rollback usable during an incident. At 3 a.m. in production, "change one line in `application.yml` + rolling restart" and "write code, compile, regression-test, deploy" are two completely different magnitudes of response. Keeping the rollback path inside the configuration surface is what makes it an incident-response tool rather than a roadmap item.

4. **A KV table that deliberately has no entity class**. `app_state` goes through a raw-SQL mapper with no entity mapping—avoiding a business-semantics-free entity for a pure KV structure, and preventing the table from being mistaken for a business table and gaining ad-hoc columns.

## Trade-off

### Costs

- **TTL is no longer a middleware-native capability**. Redis reclaims expired keys through its memory manager; in MySQL, expired rows rely on read-path filtering plus a scheduled job—stop the job and the table bloats.
  - Mitigation: read-path filtering decouples correctness from cleanup; the cleanup job has batch caps to avoid giant transactions; `idx_expire_time` keeps both filter and cleanup index-backed.

- **The two implementations have different clock domains**. The MySQL implementation writes `expire_time` with the application clock but judges expiry with the database's `UNIX_TIMESTAMP()`; Redis uses its own clock. Cross-implementation switching shifts the expiry boundary; application/database clock drift makes even the MySQL implementation internally inconsistent.
  - Mitigation: the contract must specify **clock ownership** (unifying on the database clock is a candidate fix); a **shared contract test across both implementations** locking the six-primitive semantics is the top gap to close.

- **`increment` on a key previously written by `set` with a non-integer yields 0** (`CAST(... AS SIGNED)` on a JSON string gives 0). This is a contract gap, not an implementation bug—the "counter key" and "value key" keyspaces are not force-separated.
  - Mitigation: contract forbids `increment` keys from being written via `set` with non-integers; use key prefixes to separate counter keys from value keys; contract tests cover this crossover case.

- **Single-instance assumption**. The cleanup job has no distributed lock—correct under one instance; scale-out causes duplicate cleanup (harmless but wasteful) and write amplification.
  - Mitigation: single-instance is a documented hard constraint, not a transient state; multi-instance deployment requires locking or sharding the cleanup job first.

### Benefits

- **Mandatory middleware 2 → 1**: backup, monitoring, upgrades, and failure domains all shrink—for a small team this pays off daily;
- **Zero-code rollback**: `RedisStateStore` stays resident, wired by its conditional annotation—the escape hatch is always warm;
- **The capacity decision is evidence-based**: convergence was not "Redis feels useless" but followed access-pattern analysis and headroom calculation.

## Future Evolution

1. **Contract tests (top priority)**: a parameterized test running the same assertion set against both implementations, locking six-primitive semantics—especially expiry boundaries and the `setIfAbsent` × concurrent `increment` crossover;
2. **Clock ownership convergence**: evaluate writing `expire_time` exclusively from the database clock to eliminate mixed clocks inside the MySQL implementation;
3. **Conditions for reintroducing Redis**: when a state requirement appears that is **not key-level atomic** (Hash structures for session affinity, cross-key transactions, true pub/sub), or measured MySQL point-lookup P99 breaches budget—then `app.state.store=redis` is the first move, not a rewrite.

---

See also: [Why MyBatis over JPA](why-mybatis-not-jpa.md) · [Data Permission Model](../../architecture/data-permission-model.md)
