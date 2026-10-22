# ADR: Backend Framework Switched from Planned NestJS to a Spring Boot Single Track

[中文](../../decisions/why-spring-boot-not-nestjs.md) | English

> Status: Accepted
> Date: 2026-09 (planned) → 2026-10 (converged to single track)
> Decision Maker: Architecture Team

---

## Background

The planned backend stack was **NestJS + MySQL 8 + Redis + COS + Docker**: a Node full-stack approach (miniprogram and backend in one language), TypeScript decorators with an Angular-like feel, fast iteration.

During implementation it was replaced wholesale with **Java 17 + Spring Boot 3.2 as a single track**: Redis was demoted to an [optional pluggable](why-redis-optional.md), COS was never integrated (local disk), Docker went unused; the Node implementation was **frozen** and kept only as a cross-language baseline for differential testing.

This replacement is often challenged as "team stack preference." This ADR exists to state **four benefits verifiable from the code**—if the benefits cannot be tied to concrete framework mechanisms, preference should never be written up as a decision.

## Decision

Single-track backend on Java 17 + Spring Boot 3.2. The planned NestJS implementation is discontinued; the existing Node baseline is frozen as a comparison reference and never enters production.

## Alternatives Considered

### Option A: Proceed with NestJS as planned

| Dimension | NestJS | Spring Boot |
|-----------|--------|-------------|
| Language consistency | Same JS/TS ecosystem as the miniprogram | Separate JVM stack |
| Local iteration speed | Seconds (`ts-node` / `nest start`) | Seconds to tens of seconds (Spring context) |
| Conditional wiring | Custom provider logic; no declarative equivalent | `@ConditionalOnProperty` and the annotation family |
| Deterministic startup hook | `onModuleInit`, but failure semantics vary by framework version | `@PostConstruct`; failure = context refuses to start |
| Migration orchestration | TypeORM/Knex must be wired manually | Flyway executes with the Spring lifecycle |
| Auditable strong typing | TS types vanish after compilation | JVM bytecode retains types; auditable post-hoc |

### Option B: Run both tracks in parallel (NestJS in production + progressive Java replacement)

The state that actually existed briefly during planning. **Rejected**: dual tracks mean contract maintenance ×2, deployment surface ×2, and a standing argument about "which track is authoritative"; at 24 endpoints, dual-track cost exceeded benefit immediately. After convergence, the Node side survives as a **frozen baseline—read-only, unmaintained**.

## Why Spring Boot

Each of the following points names a concrete mechanism—none rest on unfalsifiable claims like "Java is more stable":

1. **`@ConditionalOnProperty` → pluggable middleware**. The dual-implementation [StateStore](why-redis-optional.md) wires by configuration with zero-code rollback—possible because the framework provides declarative conditional wiring. NestJS's equivalent is hand-written provider factory logic—doable, but every pluggable point re-implements the selection mechanism with no unified audit surface.

2. **`@PostConstruct` → production fail-fast guardrails become possible**. This is the most important guardrail in the project: when critical configuration is missing (JWT secret too short, data directory unwritable) or a debug switch is accidentally left on in production, **the application refuses to start** instead of running degraded:

   ```java
   @PostConstruct
   public void validate() {
       if (!env.acceptsProfiles(Profiles.of("dev", "test"))) {
           // Production profile: missing secret / debug SMS / auto-provisioning on → throw, refuse to start
           failFast();
       }
   }
   ```

   Its precondition is a framework with **deterministic startup hooks whose failure blocks context assembly**. In Spring, a failed `@PostConstruct` = failed bean creation = the app never comes up; that semantic is the entire reason the guardrail holds.

3. **Flyway is framework-managed: migrations share the startup lifecycle**. "Forgot to run DDL" cannot happen—migrations run before the context is ready, and failure blocks startup. For a team with no dedicated DBA where schema changes are gated by code review, this eliminates a whole class of human error.

4. **Strong typing + compile-time checks surface contract drift early**. At 4.4k lines and 24 endpoints, a renamed DTO field or changed response shape fails at compile time or startup—not in joint debugging discovered by the frontend. The equivalent Node-side failure surfaces at runtime—the later the discovery, the higher the fix cost.

Choosing Spring also does not mean accepting its ecosystem wholesale—the **deliberately excluded parts are equally part of the decision**: no full Spring Security filter chain (only the `spring-security-crypto` module for BCrypt; authentication via a hand-written interceptor with a more legible path), validation via `spring-boot-starter-validation` rather than home-grown. **Take the framework's mechanisms, not its full suite.**

## Trade-off

### Costs

- **Iteration startup speed drops**. JVM compilation plus Spring context assembly turn local boot from seconds into tens of seconds; the edit-one-line-and-restart feedback loop lengthens.
  - Mitigation: tests favor pure unit tests (no full context); Spring DevTools hot restart as needed; the "change one line, glance at it" workflow yields to "tests first."

- **Dual-stack residue**. The frozen Node baseline still lives in the repository; build scripts and contract tests require understanding both stacks; onboarding costs more than a single-stack repo.
  - Mitigation: the Node directory is explicitly labeled "frozen, read-only, no feature commits"; differential tests are narrowed to a handful of critical contracts (e.g., crypto interop), never a full-endpoint mirror.

- **Framework magic requires disciplined countermeasures**. Auto-configuration means "who instantiated this bean and when" cannot be answered by reading `main()`; miscombined conditional annotations produce "wired when you thought it wasn't" gaps.
  - Mitigation: pluggable points are converged behind few interfaces (like `StateStore`) with contract tests; startup guardrails escalate wiring errors to "refuse to start" instead of silent degradation.

- **Skill surface narrows**. Single-track means abandoning continuous Node backend practice; returning to the Node ecosystem later requires climbing back up.
  - Mitigation: accept this cost—skill-surface narrowing is the other face of the depth-for-breadth trade that defines single-track's benefits; the cross-language differential baseline preserves a minimal context for reading Node code.

### Benefits

- **Four mechanism-level benefits** (conditional wiring / startup hooks / migration lifecycle / compile-time checks) each point to verifiable code, not preference narratives;
- **Operations surface shrinks**: no Docker dependency, middleware halved (see the [state storage ADR](why-redis-optional.md)), single-process deployment topology;
- **Audit-friendly**: bytecode retains type information; the dependency tree is reviewable via `dependency:tree`—for a project that must prove properties to third parties, this is harder evidence than "code is documentation."

## Future Evolution

1. **Narrowing the frozen Node baseline's purpose**: keep it only for cross-language interop differential testing (e.g., bit-exact crypto comparison) and contract snapshots—no feature synchronization;
2. **Continued Spring trimming**: Security's filter chain and the object-storage SDK remain out; future additions are evaluated one by one against real admin-backend needs, maintaining "à la carte" discipline;
3. **If team shape or ecosystem changes**: the re-evaluation trigger for the single-track decision is "a hard requirement only the Node ecosystem can solve" (e.g., isomorphic full-stack rendering), not "Node iterates faster"—the latter has already been quantitatively offset by the iteration-speed cost above.

---

See also: [State Storage: Redis Optional](why-redis-optional.md) · [Why MyBatis over JPA](why-mybatis-not-jpa.md) · [Audit Snapshot Pattern](../architecture/audit-and-compliance.md)
