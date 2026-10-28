# About This Book

[中文](../preface.md) | English

## What This Is

This is a practical book about **Java enterprise architecture patterns**. All content is distilled and abstracted from real production systems.

Unlike architecture books on the market, this book has the following characteristics:

| Characteristic | Description |
|----------------|-------------|
| **Production-validated** | Every pattern comes from a real project (jewelry ERP + IoT backend), not from a textbook example |
| **Honest record** | Technical decisions discuss not only benefits but also honestly record the costs that materialized |
| **Runnable code** | 4 case studies include complete tests (all 124 tests pass) |
| **Evolution perspective** | Shows the abstraction process from industry-specific to general-purpose patterns |

## Who This Is For

- **Backend architects**: Looking for architecture patterns they can put into practice
- **Mid-level Java developers**: Want to understand how to build business systems that go beyond CRUD
- **Tech leads**: Need trade-off analysis for technology selection
- **Startup teams**: A reference guide for building enterprise systems from scratch

## How to Read

### If You Want a Quick Overview

Start with [Part 1: Foundation Patterns](architecture/data-permission-model.md). These 4 patterns are the shared foundation for the commission, settlement, and compliance chapters that follow.

### If You Are Preparing for Technology Selection

Go directly to [Part 3: Technical Decisions](decisions/why-session-not-jwt.md). Each ADR includes a complete trade-off analysis.

### If You Want Hands-On Practice

Follow [Part 5: Building from Scratch](../tutorial/01-domain-model.md) in order, and complete a full distribution system in 7 steps.

### If You Have a Specific Scenario

Browse [Part 4: Case Studies](../case-studies/mto-workflow-engine/) to find a case similar to your scenario.

## Content Sources

The architecture patterns in this book come from two real production systems:

> Each architecture chapter carries a **Verification** / **Evidence boundary** line under its title: runnable implementations link to their test entry point; the rest state their source and limits explicitly, so single-team experience is not mistaken for an industry conclusion.

### System One: Jewelry Processing ERP

| Dimension | Data |
|-----------|------|
| API Endpoints | 152 |
| Database Tables | 44 |
| Test Cases | 909 |
| Tech Stack | Java 17 + Spring Boot 3.2 + MyBatis-Plus |
| Business Modules | Production, Sales, Finance, Inventory, Quality, Master Data |

### System Two: IoT Device Management Backend

| Dimension | Data |
|-----------|------|
| Registered User Capacity | ~600k |
| REST Endpoints | 24 |
| Unit Tests | 155 |
| Tech Stack | Java 17 + Spring Boot 3.2 |

> The source projects are private repositories. Only general architecture patterns are extracted; no business-sensitive configuration or customer data is included.

## Open Source License

This book is open source under the [Apache License 2.0](https://github.com/StevenTsai/distribution-architecture/blob/main/LICENSE).

Issues and Pull Requests to improve the content are welcome.

## Contact the Author

- GitHub: [@StevenTsai](https://github.com/StevenTsai)
- Project repository: [distribution-architecture](https://github.com/StevenTsai/distribution-architecture)
