# Distribution Architecture

[中文](../README.md) | English

A set of production-validated design documents for building a B2B distribution management system using Spring Boot + MyBatis, showcasing engineering patterns for data permissions, commission flows, audit logging, and compliance.

## What Is This

This is a collection of **design documents** + a **minimal runnable example**, documenting the complete process of building a distribution management system from scratch:

- **4 Core Design Patterns**: Data permissions, commission pipeline, audit logging, MyBatis engineering
- **3 Architecture Decision Records**: Session vs JWT, MyBatis vs JPA, Logical vs Physical Delete
- **7-Step Build-from-Scratch Tutorial**: Complete narrative from domain model to compliance checks
- **4 Practical Guides**: Quick start, extending new modules, customizing permissions, Spring Boot 3 migration

## Who Is This For

| Role | What You'll Get |
|------|----------------|
| **Backend Architect** | Production-ready patterns for data permissions, commission flows, audit logging |
| **Mid-level Java Developer** | How to build business systems that go beyond CRUD |
| **Tech Lead** | Trade-off analysis for technology decisions (ADRs) |
| **Startup CTO** | Domain model for distribution systems, shorter time-to-market |

## Document Structure

```
├── distribution-starter/            Minimal runnable example (clone and run)
│   ├── src/main/java/               Spring Boot + MyBatis complete skeleton
│   ├── src/main/script/sql/         Database initialization scripts
│   └── README.md                    Quick start guide
│
├── architecture/                    Design patterns (core value)
│   ├── data-permission-model.md     4-level data permissions: SQL-layer row filtering
│   ├── commission-pipeline.md       Commission pipeline: event-driven state machine + rule snapshots + reversal
│   ├── audit-and-compliance.md      Audit logging: JSON snapshots + unified entry point
│   └── mybatis-patterns.md          MyBatis engineering: 3-segment SQL fragments + dual queries
│
├── decisions/                       Architecture Decision Records (ADR)
│   ├── why-session-not-jwt.md       Why Session Token over JWT
│   ├── why-mybatis-not-jpa.md       Why MyBatis over JPA
│   └── why-logical-delete.md        Why logical delete over physical delete
│
├── guides/                          Practical guides
│   ├── quick-start.md               Get running in 5 minutes
│   ├── extend-new-module.md         How to extend a new module
│   ├── customize-permission.md      How to customize the permission model
│   └── spring-boot-3-migration.md   Spring Boot 3.x migration guide
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

## Recommended Reading

**If you want to try it out**: Go to `distribution-starter/` and follow the README to get running in 5 minutes.

**If you want a quick overview**: Start with the 4 design pattern documents in `architecture/`.

**If you want to understand from the beginning**: Read `tutorial/` in order (01→07) — this is the complete build-from-scratch narrative.

**If you're making technology decisions**: Read the 3 ADRs in `decisions/` for trade-off analysis.

**If you're building on top of this**: Read the practical guides in `guides/`.

## Core Design Philosophy

### 1. Data Permissions at the SQL Layer

Instead of manually filtering data in each Service method, permission conditions are automatically injected into SQL via MyBatis `<sql>` fragments. Developers only need to choose between "query with permissions" or "query without permissions."

### 2. Event-Driven, Not State-Driven

Business order status changes are driven by "events," not direct state modifications. Each event automatically triggers: status update → commission calculation → lead synchronization → audit logging, all within a single transaction.

### 3. Rule Snapshots, Not References

Commission ledger entries store "what the rule was at that time" (JSON snapshot), not "what the rule ID is." This ensures that even if policies change, generated commissions remain fully traceable.

### 4. Reversal, Not Deletion

When a business order is cancelled, generated commissions are not deleted. Instead, negative reversal entries are created. Commissions that have already been settled generate offset entries to be deducted in the next period, following the accounting convention of "red-letter reversal."

### 5. Audit Logs Separated from Compliance Records

Audit logs are "passive recording" (all operations automatically recorded), while compliance records are "active management" (requiring approval workflows). The two complement each other, meeting different dimensions of traceability requirements.

## Tech Stack

- **Framework**: Spring Boot 3.2.5
- **Language**: Java 17
- **ORM**: MyBatis 3.0.3
- **Database**: MySQL 8.0
- **Cache**: Redis
- **API Docs**: SpringDoc 2.3.0 (OpenAPI 3)

## License

[Apache License 2.0](../LICENSE)
