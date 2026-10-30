# Distribution Architecture

[![Deploy Docs](https://github.com/StevenTsai/distribution-architecture/actions/workflows/deploy.yml/badge.svg)](https://github.com/StevenTsai/distribution-architecture/actions/workflows/deploy.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/StevenTsai/distribution-architecture/blob/main/LICENSE)

[中文](https://github.com/StevenTsai/distribution-architecture/blob/main/README.md) | English

📖 **[Read Online](https://steventsai.github.io/distribution-architecture/)** | B2B enterprise architecture design pattern library — reusable architecture solutions and full-lifecycle methodology refined from production systems.

📦 **Gitee mirror** (auto-synced, GitHub is the source of truth): [gitee.com/steven27/distribution-architecture](https://gitee.com/steven27/distribution-architecture) — faster cloning for readers in China.

## What Is This

This is a collection of **architecture design documents** + a **reusable Starter library** + **production-validated case studies** + a **full-lifecycle methodology**, covering an enterprise system from build, selection, and verification through takeover, portfolio governance, and production operations.

Beyond code, it distills seven directly applicable methods: **takeover engineering** (should you accept it, how much to invest, when to stop), **system portfolio governance** (what to shut down or remediate), **production operations** (observability, change management, incident response), **AI engineering** (structured output, model routing, and long-document pipelines), **enterprise architecture** (DDD and practical TOGAF tailoring), **engineering consistency standards** (naming, interfaces, release, and glossary baselines), and **open-source compliance** (what stays private, leak scanning, pre-push checks). The playbooks are written in Chinese; see the [playbooks index](playbooks.md).

> I encountered generic problems in a production system: data permissions, workflow engines, event-driven decoupling, piecework wages.
> I first implemented these patterns with Spring Boot + MyBatis in a real project,
> then summarized them as design documents and ADRs,
> and finally abstracted them into reusable components and open-sourced them.

### Design Patterns and Architecture Methods (9)
- [Data permissions at the SQL layer](architecture/data-permission-model.md)
- [Event-driven state machine (commission pipeline)](architecture/commission-pipeline.md)
- [Audit logging with JSON snapshots](architecture/audit-and-compliance.md)
- [MyBatis engineering patterns](architecture/mybatis-patterns.md)
- [PII encryption & masking (AES-GCM + HMAC lookup keys)](architecture/pii-encryption.md)
- [Admin RBAC (module-level permissions + sensitive operation control)](architecture/admin-rbac.md)
- [Cross-language contract testing (outside-the-arbiter + fixture comparison)](architecture/cross-language-contract-testing.md)
- [Cell-based architecture](architecture/cell-based-architecture.md) — blast radius, unit keys, data sharding, failure drills
- [Three forms of service governance](architecture/service-governance-forms.md) — SDK / Agent / Sidecar decision matrix

### AI Systems & Engineering
- [AI Agent Architecture](architecture/agent-architecture-patterns.md) — declarative orchestration, state machines, risk-based guardrails
- [Component-Correct ≠ System-Effective](architecture/verifier-failure-modes.md) — an end-to-end failure postmortem: offline 100% vs E2E 30–46%
- [Claim-Level Verification](architecture/claim-level-verifier.md) — evidence-bound claims and deterministic gates, backed by [GroundedRAG](https://github.com/StevenTsai/grounded-rag)
- [AI Engineering Playbook](../playbooks/ai-engineering/) (Chinese) — structured output, multi-model routing, and long-document pipelines

### Reusable Library
- [spring-data-permission-starter](https://github.com/StevenTsai/spring-data-permission-starter) — Spring Boot Starter for data permissions, ready to import
- [grounded-rag](https://github.com/StevenTsai/grounded-rag) — Claim-level verification gate RAG reference implementation (Python / MIT), the evidence source for the AI systems reliability chapters

### Production-Validated Case Studies (4 code cases, 124 tests + 1 evolution narrative)
- [MTO Workflow Engine](case-studies/mto-workflow-engine/) — Generic work order state machine extracted from a jewelry ERP, reusable across furniture/electronics/apparel industries
- [Spring Events Decoupling](case-studies/spring-events-decoupling/) — Event-driven cross-module decoupling: delivery→receivable / work order→inventory / quality→rework
- [Piecework Wage Engine](case-studies/piecework-wage-engine/) — Pipeline-based piecework wage calculation with configurable difficulty and quality coefficients
- [Production Guardrails: fail-fast startup + pluggable state store](case-studies/production-guardrails/) — Mandatory middleware 2→1, zero-code rollback, contract tests filled in (from an IoT backend, not the jewelry ERP)
- [OncoHub Architecture Evolution](case-studies/oncohub-architecture-evolution.md) — A design document rewritten eight times: reading code to correct assumptions, auth decoupling, data-flow direction, and staged rollout (narrative case)

### Architecture Decision Records (6)
- [Session vs JWT](decisions/why-session-not-jwt.md), [MyBatis vs JPA](decisions/why-mybatis-not-jpa.md), [Logical vs Physical Delete](decisions/why-logical-delete.md)
- [Spring Boot single track over planned NestJS](decisions/why-spring-boot-not-nestjs.md) — Four mechanism-level benefits of the framework switch, not preference narratives
- [State storage in MySQL, Redis pluggable](decisions/why-redis-optional.md) — Mandatory middleware 2→1, zero-code rollback
- [Zero build chain: JavaScript over TypeScript](decisions/why-javascript-not-typescript.md) — Honest record of realized costs and root-cause revision

### Tutorials & Guides
- **7-Step Build-from-Scratch Tutorial**: Complete narrative from domain model to compliance checks
- **4 Practical Guides**: Quick start, extending new modules, customizing permissions, Spring Boot 3 migration

### Methodology Playbooks (7, Chinese)
- [Takeover Engineering](../playbooks/takeover-engineering/) — should you take it, how much to invest, when to stop
- [System Portfolio Governance](../playbooks/system-portfolio-governance/) — inventory, diagnosis, shutdown/merge decisions, anti-backslide
- [Production Operations](../playbooks/production-operations/) — observability, change, incident response, drills
- [AI Engineering](../playbooks/ai-engineering/) — structured output, model routing, and long-document orchestration
- [Enterprise Architecture](../playbooks/enterprise-architecture/) — DDD landing and practical TOGAF tailoring
- [Engineering Consistency Standards](../playbooks/engineering-consistency-standards.md) — naming, interface, release, and glossary baselines
- [Open-Source Compliance](../playbooks/open-source-compliance.md) — split lines, leak scanning, and pre-push checks

## Production Source

The case study code is extracted and abstracted from a **real production system** (jewelry processing ERP):

| Dimension | Data |
|-----------|------|
| API Endpoints | 152 |
| Database Tables | 44 |
| Test Cases | 909 |
| Tech Stack | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 |
| Business Modules | Production, Sales, Finance, Inventory, Quality, System |

> The source project is a private repository. Only generic architecture patterns are extracted; no business-sensitive configuration or customer data is included. The 909 tests are a source-system metric; the four case studies in this repository contain 124 runnable tests.

The fourth case study, [production-guardrails](case-studies/production-guardrails/), is extracted from **a second private production system** (an IoT device-management backend, battery-telemetry domain): ~600k registered-user capacity envelope, 24 REST endpoints, and 155 source-system unit tests; the extracted guardrails case contains 51 tests. Only generic architecture patterns are carried over.

## Case Study Abstraction Methodology

Four principles for extracting generic patterns from domain-specific systems:

| Principle | Description | Example |
|-----------|-------------|---------|
| **Identify specialization anchors** | Find fields/logic that clearly belong only to the current business | Gold/Stone → `InventoryQueryService` interface |
| **Interface-isolate variations** | Use interfaces to encapsulate changeable parts | 17 fixed processes → YAML-configurable route |
| **Preserve patterns, replace details** | Keep architectural patterns, replace business specifics | State machine pattern kept, specific state values replaced |
| **Make it work first, then generic** | Get it running first, validate, then abstract | Implement `piecePrice × qty` first, add coefficients later |

## Who Is This For

| Role | What You'll Get |
|------|----------------|
| **Backend Architect** | Production-ready patterns for data permissions, work order state machines, event-driven decoupling |
| **Mid-level Java Developer** | How to build business systems that go beyond CRUD, and how to design a Starter library |
| **Tech Lead** | Trade-off analysis for technology decisions (ADRs), plus portfolio governance decisions ([System Portfolio Governance](playbooks.md)) |
| **AI application engineers / architects** | AI agent architecture patterns, component-correct ≠ system-effective, and the [claim-level verification gate](architecture/claim-level-verifier.md) |
| **Operations / SRE** | [Production operations](playbooks.md) — observability, change and staged rollout, incident response, capacity and drills |
| **Pre-sales / business decision-makers** | [Takeover engineering](playbooks.md) — how to assess, price, and when to walk away before accepting a system |
| **Developers needing data permissions** | Directly use [spring-data-permission-starter](https://github.com/StevenTsai/spring-data-permission-starter) |

## Document Structure

```
├── distribution-starter/            Minimal runnable example (initialize MySQL, then run)
│   ├── src/main/java/               Spring Boot + MyBatis complete skeleton
│   ├── src/main/script/sql/         Database initialization scripts
│   └── README.md                    Quick start guide
│
├── architecture/                    Design patterns (core value)
│   ├── data-permission-model.md     4-level data permissions: SQL-layer row filtering
│   ├── commission-pipeline.md       Commission pipeline: event-driven state machine + rule snapshots + reversal
│   ├── audit-and-compliance.md      Audit logging: JSON snapshots + unified entry point
│   ├── mybatis-patterns.md          MyBatis engineering: 3-segment SQL fragments + dual queries
│   ├── pii-encryption.md            PII encryption: AES-GCM + HMAC lookup keys + unified masking
│   ├── admin-rbac.md                Admin RBAC: module-level permissions + sensitive operation control
│   ├── cross-language-contract-testing.md  Cross-language contract testing: outside-the-arbiter + fixture comparison
│   ├── cell-based-architecture.md   Cell-based architecture: blast radius, unit keys, sharding, failure drills
│   ├── service-governance-forms.md  Service governance forms: SDK / Agent / Sidecar decision matrix
│   ├── agent-architecture-patterns.md  AI agent architecture: declarative vs imperative, risk-tiered guardrails
│   ├── verifier-failure-modes.md    Component-correct ≠ system-effective: E2E failure postmortem
│   └── claim-level-verifier.md      Claim-level verification: four-step gate + same-source invariants
│
├── decisions/                       Architecture Decision Records (ADR)
│   ├── why-session-not-jwt.md       Why Session Token over JWT
│   ├── why-mybatis-not-jpa.md       Why MyBatis over JPA
│   ├── why-logical-delete.md        Why logical delete over physical delete
│   ├── why-spring-boot-not-nestjs.md  Spring Boot single track over NestJS
│   ├── why-redis-optional.md        State storage in MySQL table, Redis pluggable
│   └── why-javascript-not-typescript.md  Zero build chain on the client (honest cost record)
│
├── guides/                          Practical guides
│   ├── quick-start.md               Get running in 5 minutes
│   ├── extend-new-module.md         How to extend a new module
│   ├── customize-permission.md      How to customize the permission model
│   └── spring-boot-3-migration.md   Spring Boot 3.x migration guide
│
├── case-studies/                    Production-validated case studies (extracted from multiple private systems)
│   ├── mto-workflow-engine/         MTO Workflow Engine
│   │   ├── WorkOrderStateMachine    9-state work order state machine
│   │   ├── ProcessRouteConfig       YAML process route configuration
│   │   └── MaterialCheckService     Material readiness check
│   ├── spring-events-decoupling/    Spring Events Cross-Module Decoupling
│   │   ├── DeliveryCompleteEvent    Delivery → Accounts Receivable
│   │   ├── WorkOrderFinishedEvent   Completion → Finished Goods
│   │   └── QualityDefectEvent       Quality → Rework Order
│   ├── piecework-wage-engine/       Piecework Wage Calculation Engine
│   │   ├── WageCalculator           Wage calculation engine
│   │   ├── WageSettlementPipeline   Pipeline orchestrator
│   │   └── 4 Pipeline Stages        Calculate → Adjust → Confirm → Summary
│   ├── production-guardrails/       Production guardrails (fail-fast startup + pluggable state store)
│   │   ├── StartupGuard             Config red-line rejection matrix
│   │   ├── StateStore               Six-primitive contract, three impls (mysql/redis/memory)
│   │   └── Contract test suite      51 tests, zero external services (H2 MODE=MySQL)
│   └── oncohub-architecture-evolution.md  Architecture evolution narrative: eight design-doc revisions
│
├── playbooks/                       Methodology playbooks (Chinese-only)
│   ├── takeover-engineering/        Takeover toolkit: 21 questions + 10 templates
│   ├── system-portfolio-governance/ Portfolio governance: inventory → diagnosis → decisions → anti-backslide
│   ├── production-operations/       Production operations: observability, change, response, drills
│   └── engineering-consistency-standards.md  Team naming/interface/release baseline
│
├── CONTRIBUTING.md                  Contribution guidelines
│
└── tutorial/                        Build-from-scratch series (recommended reading order)
    ├── 01-domain-model.md           Domain model: core entities and relationships
    ├── 02-data-permission.md        Data permissions: resolution and SQL injection
    ├── 03-crud-scaffolding.md       CRUD scaffolding: Mapper → Service → Controller
    ├── 04-audit-logging.md          Audit logging: unified entry point and JSON snapshots
    ├── 05-commission-flow.md        Commission flow: event progression and rule matching
    ├── 06-settlement.md             Settlement process: aggregation → review → payment
    └── 07-compliance.md             Compliance checks: review process and business integration
```

## English Documentation

This directory mirrors the Chinese book: the preface, all 9 Java-era architecture patterns, the 3 AI systems articles, all 6 ADRs, all 4 practical guides, the 7-step tutorial, and the case-study READMEs plus their evolution notes (including the OncoHub narrative). The seven methodology playbooks (including the AI engineering playbook) remain Chinese-only; see the [playbooks index](playbooks.md) for what they cover and how to navigate them.

| Section | Documents |
|---------|-----------|
| Overview | [Preface](preface.md) |
| Architecture | [Data Permission Model](architecture/data-permission-model.md) · [Commission Pipeline](architecture/commission-pipeline.md) · [Audit Snapshot](architecture/audit-and-compliance.md) · [MyBatis Engineering](architecture/mybatis-patterns.md) · [PII Encryption](architecture/pii-encryption.md) · [Admin RBAC](architecture/admin-rbac.md) · [Cross-Language Contract Testing](architecture/cross-language-contract-testing.md) · [Cell-Based Architecture](architecture/cell-based-architecture.md) · [Service Governance Forms](architecture/service-governance-forms.md) · [AI Agent Architecture](architecture/agent-architecture-patterns.md) · [Verifier Failure Modes](architecture/verifier-failure-modes.md) · [Claim-Level Verification](architecture/claim-level-verifier.md) |
| Playbooks | [Methodology Playbooks Index (Chinese)](playbooks.md) |
| Decisions | [Session vs JWT](decisions/why-session-not-jwt.md) · [MyBatis vs JPA](decisions/why-mybatis-not-jpa.md) · [Logical Delete](decisions/why-logical-delete.md) · [Spring Boot over NestJS](decisions/why-spring-boot-not-nestjs.md) · [Redis Optional](decisions/why-redis-optional.md) · [JavaScript over TypeScript](decisions/why-javascript-not-typescript.md) |
| Guides | [Quick Start](guides/quick-start.md) · [Extend a New Module](guides/extend-new-module.md) · [Customize Permissions](guides/customize-permission.md) · [Spring Boot 3 Migration](guides/spring-boot-3-migration.md) |
| Tutorial | [01 Domain Model](tutorial/01-domain-model.md) · [02 Data Permissions](tutorial/02-data-permission.md) · [03 CRUD Scaffolding](tutorial/03-crud-scaffolding.md) · [04 Audit Logging](tutorial/04-audit-logging.md) · [05 Commission Flow](tutorial/05-commission-flow.md) · [06 Settlement](tutorial/06-settlement.md) · [07 Compliance](tutorial/07-compliance.md) |
| Case studies | [MTO Workflow Engine](case-studies/mto-workflow-engine/) · [Piecework Wage Engine](case-studies/piecework-wage-engine/) · [Spring Events Decoupling](case-studies/spring-events-decoupling/) · [Production Guardrails](case-studies/production-guardrails/) · [OncoHub Evolution](case-studies/oncohub-architecture-evolution.md) |

Each English page links back to its Chinese original at the top. Translations track the Chinese source; if you change either version, update both.

Project-level documents are also available in English: [Contributing](CONTRIBUTING.md) · [Security Policy](SECURITY.md) · [Starter README](distribution-starter/README.md).

## Recommended Reading

**If you want to try it out**: Go to `distribution-starter/`, follow the README to initialize MySQL, and start the application.

**If you want a quick overview**: Start with the four foundational design pattern documents in `architecture/` (data permissions, event-driven flows, audit, and MyBatis), then read the other five.

**If you want to see production-validated case studies**: Go to `case-studies/` — each code case study has a README and an evolution document; the OncoHub entry is a narrative case.

**If you want to understand from the beginning**: Read `tutorial/` in order (01→07) — this is the complete build-from-scratch narrative.

**If you're making technology decisions**: Read the 6 ADRs in `decisions/` for trade-off analysis. For platform-level choices, see [Three Forms of Service Governance](architecture/service-governance-forms.md); for capacity ceilings or shrinking a failure domain, see [Cell-Based Architecture](architecture/cell-based-architecture.md).

**If you run systems in production**: The [methodology playbooks](playbooks.md) cover takeovers, portfolio governance, and production operations. They are written in Chinese.

**If you're building AI systems**: Start with [AI Agent Architecture](architecture/agent-architecture-patterns.md), then [Component-Correct ≠ System-Effective](architecture/verifier-failure-modes.md) and [Claim-Level Verification](architecture/claim-level-verifier.md) for output reliability; both are backed by the open-source reference implementation [GroundedRAG](https://github.com/StevenTsai/grounded-rag).

**If you're building on top of this**: Read the practical guides in `guides/`.

## Tech Stack

- **Framework**: Spring Boot 3.2.5
- **Language**: Java 17
- **ORM**: MyBatis (mybatis-spring-boot-starter 3.0.3)
- **Database**: MySQL 8.0
- **Cache**: Redis (optional)
- **API Docs**: SpringDoc 2.3.0 (OpenAPI 3)

## License

[Apache License 2.0](https://github.com/StevenTsai/distribution-architecture/blob/main/LICENSE)
