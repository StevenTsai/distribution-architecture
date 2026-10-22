# Case Study: Production Guardrails

[中文](../../../case-studies/production-guardrails/README.md) | English

Two production guardrails extracted from a live IoT device-management backend: a **fail-fast startup guard** (refuse to start rather than run degraded) and a **pluggable state store** that cut mandatory production middleware from two components to one while keeping a zero-code rollback path.

## What Is This

Both guards address the same failure class: **misconfiguration that fails silently in production and loudly in hindsight**. One turns configuration violations into startup failures; the other turns a middleware decision into a configuration switch with a tested escape hatch.

## Background

The source system is a Spring Boot backend serving an IoT device platform (~600k registered-user capacity envelope). Before converging, its short-lived state (verification codes, token denylists, handshake material) was hosted on Redis next to MySQL. Access-pattern analysis showed three shared properties—short TTL, key-level atomicity, regenerable data—and a few hundred QPS peak that a single MySQL table absorbs with 15–50× headroom. The question became: *is a resident Redis worth it?*

The full decision narrative lives in the ADRs; this case study is the **runnable code** behind them:

- [ADR: State Storage in MySQL, Redis Optional](../../decisions/why-redis-optional.md)
- [ADR: Spring Boot Single Track over NestJS](../../decisions/why-spring-boot-not-nestjs.md)

## Guard 1: StartupGuard — Fail-Fast on Misconfiguration

`@PostConstruct` runs one check pass; any violation in a non-dev/test profile **aborts context startup** instead of letting the app run degraded:

| Check | dev/test | prod | Consequence stated in the rejection |
|---|---|---|---|
| `app.state.store` ∈ {mysql, redis, memory} | reject (always) | reject (always) | typo would wire no `StateStore` at all |
| `app.jwt.secret` placeholder (`change-me*`) | warn, allow | **reject** | anyone could forge tokens |
| `app.sms.debug=true` | allow | **reject** | codes only printed to logs, never delivered |
| `app.storage.base-url` missing | allow | **reject** | download URLs fall back to request Host header (Host-injection surface) |

Two design points worth copying:

1. **Every rejection message names the consequence** ("otherwise … happens"), so the startup log is self-explanatory at 3 a.m. — no wiki lookup required.
2. **The guard only works because the framework guarantees the semantics**: a failed `@PostConstruct` = failed bean creation = the application never comes up. That is the mechanism-level reason the single-track Spring Boot decision was made (see ADR).

## Guard 2: StateStore — Middleware 2→1 with Zero-Code Rollback

```
app.state.store = mysql (default, matchIfMissing)  →  MysqlStateStore   (app_state table, zero extra middleware)
               | redis                              →  RedisStateStore   (rollback path, resident code)
               | memory                             →  InMemoryStateStore (contract reference / local dev)
               | anything else                      →  no bean (StartupGuard rejects at startup)
```

Six primitives (`set` / `get` / `setIfAbsent` / `delete` / `exists` / `increment`) with a written semantic contract; JSON values; tolerant read (corrupt value → treated as missing and deleted).

The MySQL implementation keeps atomicity **visible in SQL**—no ORM flush timing to audit:

```sql
-- setIfAbsent: primary-key conflict = key still live, naturally atomic
INSERT IGNORE INTO app_state (state_key, state_value, expire_time, update_time) VALUES (?, ?, ?, ?);

-- increment: single-statement row-lock increment, aligned with Redis INCR semantics
INSERT INTO app_state (state_key, state_value, expire_time, update_time)
VALUES (?, '1', ?, ?)
ON DUPLICATE KEY UPDATE
  state_value = CASE WHEN expire_time <= ? THEN '1' ELSE CAST(state_value AS BIGINT) + 1 END,
  expire_time = CASE WHEN expire_time <= ? THEN ? ELSE expire_time END;
```

**Clock ownership** (an evolution over the production original): expiry is judged by an **application-supplied `now` parameter** on every read/write path—one clock domain, no `UNIX_TIMESTAMP()`-in-SQL mixing, and contract-testable against H2. The production variant and the reasoning difference are recorded in [docs/from-production-to-generic.md](../../../case-studies/production-guardrails/docs/from-production-to-generic.md).

## The Contract Test Suite — The Gap, Honestly Recorded

The production system shipped the abstraction **without contract tests** (its javadoc referenced a test class that did not exist). This case study ships the missing piece:

| Suite | What it locks | Count |
|---|---|---|
| `StateStoreContractTest` (abstract) | Six-primitive semantics: JSON round-trip, tolerant read, expiry, setIfAbsent, atomic concurrent increment (final value exact) | 13 assertions-cases × 2 impls |
| `InMemoryStateStoreContractTest` | Reference implementation—the first arbiter of semantic disputes | 13 |
| `MysqlStateStoreContractTest` | Real SQL against H2 `MODE=MySQL` (`INSERT IGNORE`, `ON DUPLICATE KEY UPDATE`) | 13 |
| `StateStoreWiringTest` | Condition → implementation mapping; illegal value wires **nothing** (no silent fallback) | 5 |
| `StartupGuardTest` | The full rejection matrix, one executable assertion per production red line | 9 |

**40 tests, zero external services.** Redis semantics are pinned by the interface contract and identical JSON handling; live-Redis contract tests are listed under Not Included.

## Project Structure

```
src/main/java/com/distribution/casestudy/guardrails/
├── config/
│   ├── GuardProperties.java          # app.* config surface (deliberately domain-neutral)
│   └── StartupGuard.java             # @PostConstruct fail-fast checks
└── state/
    ├── StateStore.java               # interface + written semantic contract
    ├── InMemoryStateStore.java       # contract reference (memory mode)
    ├── MysqlStateStore.java          # default: app_state, atomic SQL visible
    ├── RedisStateStore.java          # rollback path: config switch, zero code change
    └── StateStoreConfig.java         # @ConditionalOnProperty wiring (3 lines of selection logic)
src/main/resources/
└── app-state-schema.sql              # app_state DDL (dual-dialect: H2 MySQL-mode + MySQL)
src/test/java/.../
├── config/StartupGuardTest.java      # rejection matrix
└── state/
    ├── StateStoreContractTest.java   # abstract shared assertions
    ├── InMemoryStateStoreContractTest.java
    ├── MysqlStateStoreContractTest.java
    └── StateStoreWiringTest.java     # ApplicationContextRunner wiring lock
```

## How to Run

```bash
cd case-studies/production-guardrails
mvn test        # JDK 17+; no MySQL, no Redis required
```

## Not Included

Deliberately out of scope—each with a reason:

- **Expired-row cleanup job** (the production system pruned `app_state` every 10 minutes in batches): semantics stay correct without it (read-path filter makes expired rows invisible); only the table grows. Physical cleanup is an operations concern.
- **A runnable Spring Boot application**: this is a library-style case study. Wire `StateStoreConfig` + `StartupGuard` into your app and enable `@ConfigurationProperties` binding (`@ConfigurationPropertiesScan` or `@EnableConfigurationProperties(GuardProperties.class)`).
- **Live-Redis contract tests**: require a running Redis; the interface javadoc plus identical JSON/serialization behavior carry the contract in this repo's zero-service test budget.

## Trade-offs

| Choice | Cost | Mitigation |
|---|---|---|
| H2 `MODE=MySQL` for SQL tests | Not bit-identical to real MySQL (dialect corners differ) | Contract tests lock *semantics*, not a dialect version; the SQL uses only widely-supported constructs |
| Application-clock `now` parameters | App clock drift now affects expiry judgment | One clock domain is strictly better than two; drift handling belongs to NTP/infra, not mixed in-SQL/app clocks |
| `memory` mode (not in production) | Third implementation to keep contract-aligned | It *is* the contract's first reference—the alignment burden is the point |
| `VARCHAR(2048)` over `TEXT` for `state_value` | Smaller JSON ceiling than production's TEXT | Suits the demo's value sizes; noted in the schema for anyone promoting it |

## Related Documents

- [ADR: State Storage in MySQL, Redis Optional](../../decisions/why-redis-optional.md) — the decision behind Guard 2
- [ADR: Spring Boot Single Track over NestJS](../../decisions/why-spring-boot-not-nestjs.md) — why `@PostConstruct` fail-fast was possible at all
- [Audit Snapshot Pattern](../../architecture/audit-and-compliance.md) — the same fail-fast philosophy applied to audit writes (fail-open / fail-closed grading)
- [Extraction Narrative: From Production to Generic](../../../case-studies/production-guardrails/docs/from-production-to-generic.md)

## Source

Extracted from a private production IoT device-management backend (battery-telemetry domain). Only generic architecture patterns are carried over—no client names, brands, protocol details, credentials, or business-field semantics. Capacity figures are order-of-magnitude envelopes, not measured production metrics.
