# From Production to Generic: The Guardrails Extraction Journey

[中文](../../../../case-studies/production-guardrails/docs/from-production-to-generic.md) | English

This document records what changed, what was preserved, and why—when extracting two production guardrails (fail-fast startup + pluggable state store) from a private IoT device-management backend into this runnable case study.

## Original System Overview

The source is a Java 17 + Spring Boot 3.2 backend for an IoT device platform (battery-telemetry domain, ~600k registered-user capacity envelope, 24 REST endpoints, 155 unit tests).

Two facts shaped the guardrails:

1. **Short-lived state lived on Redis beside MySQL**—verification codes, token denylists, and handshake material. Access-pattern analysis (short TTL, key-level atomicity, regenerable data, few hundred QPS peak) showed a single MySQL table absorbs the load with 15–50× headroom.
2. **Configuration could silently degrade production.** The starkest example: the only SMS sender printed codes to logs in debug mode, and nothing structurally prevented that debug switch from reaching production—where phone-number login would then fail 100% of the time, or worse, appear to work in testing and never deliver in production. The startup guard exists to make that failure class **impossible to deploy**, not merely visible.

## What Changed and Why

### 1. Config namespace: product prefix → neutral `app.*`

The production config prefix carried the product's name. The case study uses a deliberately domain-neutral `app.*`—the guard patterns are about *config hygiene*, not about any product.

### 2. Business-specific checks → generic archetypes

Production checks included third-party app secrets and a PII-encryption key. Those are business wiring, not patterns. The case study keeps the **archetypes** that generalize:

| Production check | Kept as | Why it generalizes |
|---|---|---|
| Third-party app secret missing | `app.storage.base-url` missing | "Missing required production config" with stated consequence |
| Debug SMS switch | `app.sms.debug` | "Fake I/O in production" switch |
| Placeholder JWT secret | `app.jwt.secret` (`change-me*` prefix) | "Template value shipped to prod" |
| Storage-mode enum | `app.state.store` enum | "Typo silently selects nothing" |

Every rejection message keeps the production discipline: **state the consequence inline** ("otherwise … happens"), so logs are self-explanatory.

### 3. MyBatis mapper → `JdbcTemplate`

The production `app_state` access was already entity-free raw SQL through a MyBatis mapper. The case study drops MyBatis for `JdbcTemplate`: same visible-SQL discipline, two fewer moving parts, and a test suite that needs no mapper XML or MyBatis context. (The repository's broader MyBatis-vs-ORM position lives in [the MyBatis ADR](../../../decisions/why-mybatis-not-jpa.md); this case study simply refuses to make ORM the story.)

### 4. `UNIX_TIMESTAMP()`-in-SQL → parameterized application clock

The most substantive technical change. Production judged expiry inside SQL (`WHERE expire_time > UNIX_TIMESTAMP()`) while *writing* `expire_time` from the application clock—two clock domains in one implementation, flagged as a defect in its own architecture review.

The case study **pushes `now` as a parameter** on every read/write path:

- one clock domain (application), written into the contract javadoc;
- expiry semantics become testable against H2 without emulating MySQL's clock functions;
- this implements the production ADR's own "future evolution" item (clock-ownership convergence)—the case study is what that remediation looks like.

### 5. Added `InMemoryStateStore` + `memory` mode (not in production)

Production had two implementations (MySQL, Redis). A third—pure in-memory—serves as the **contract's first reference**: semantic disputes are settled here first, with zero external dependencies, before the MySQL/Redis implementations are required to agree. Adding it is also the pattern's extensibility demo: one class + one `@Bean` + one enum value.

### 6. Added the contract test suite production lacked

The production `StateStore` interface's javadoc *claimed* a contract test class that did not exist. The abstraction shipped on documentation faith alone.

This case study closes that gap: one abstract assertion set (`StateStoreContractTest`) executed against the in-memory and H2/MySQL implementations, covering JSON round-trip, tolerant read, expiry, `setIfAbsent`, and atomic concurrent increment (final value must be exact; concurrent read-back may be pessimistically high, matching the documented "stricter is safer" semantics). Wiring is locked by an `ApplicationContextRunner` suite whose sharpest case is **illegal value → zero beans**—silent fallback is the most dangerous wiring failure mode.

### 7. DDL: `TEXT` → `VARCHAR(2048)`, dual-dialect

`increment`'s `CAST(state_value AS BIGINT)` needs a string-typed column H2 can cast; production used `TEXT` (fine for MySQL, opaque to H2's cast). `VARCHAR(2048)` runs identically under MySQL and H2 `MODE=MySQL`. The schema file documents the production alternative for anyone promoting the pattern.

## What Was Preserved

- **Six primitives + JSON values + tolerant read** (corrupt value → treated as missing, key deleted)—the interface contract is byte-for-byte the production semantics.
- **`matchIfMissing = true`**: the operational default is the path with fewest components (MySQL), never the path with most (Redis).
- **Atomicity visible in SQL**: `INSERT IGNORE` for set-if-absent, single-statement `ON DUPLICATE KEY UPDATE` for increment—nothing hidden in ORM flush timing.
- **Fail-fast guard semantics**: refuse to start, never silently degrade; placeholder-prefix detection; dev/test leniency with warnings; rejection messages that teach.
- **`RedisStateStore` resident**: the rollback path ships in the codebase, selected by one config line—the escape hatch stays warm.

## Lessons Learned

1. **An abstraction without contract tests is a promise nobody is enforcing.** The production interface documented its semantics carefully—and nothing verified them. Ship the shared assertion set in the same commit as the interface.
2. **Clock ownership must be written into the contract, not discovered in an incident.** "Who judges expiry, with which clock" is a cross-implementation concern the moment a rollback path exists.
3. **Guard messages should teach, not just block.** A rejection log line that states the consequence turns an on-call night into a 30-second config fix.
4. **The default path should be the minimal-middleware path.** `matchIfMissing` on the MySQL implementation encodes an operations opinion in one annotation—reviewable, testable, reversible by config.
5. **Silent fallback is worse than absence.** A typo'd store mode wiring *nothing* (caught at startup by the guard) beats a typo quietly selecting the wrong implementation forever.
