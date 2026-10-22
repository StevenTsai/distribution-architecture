# Distribution Architecture

[![Deploy Docs](https://github.com/StevenTsai/distribution-architecture/actions/workflows/deploy.yml/badge.svg)](https://github.com/StevenTsai/distribution-architecture/actions/workflows/deploy.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

中文 | [English](en/README.md)

📖 **[在线阅读《架构炼金术：从真实系统提炼可复用的 Java 架构模式》](https://steventsai.github.io/distribution-architecture/)** | B2B 企业级架构设计模式库，从生产系统中提炼的可复用架构方案。

## 这是什么

这是一套**设计文档** + **可复用 Starter 库** + **生产验证案例**，记录了一个企业级系统从零构建的完整过程。

> 我在生产系统中遇到了数据权限、工作流引擎、事件驱动解耦、计件工资等通用问题。
> 我先在真实项目中用 Spring Boot + MyBatis 实现了这些模式，
> 然后总结为设计文档和 ADR，
> 最后抽象为可复用的组件并开源。

### 设计模式（7 个）
- 数据权限下沉到 SQL 层
- 事件驱动状态机（佣金流转）
- 审计日志 JSON 快照
- MyBatis 工程化
- PII 加密与脱敏（AES-GCM + HMAC 检索键）
- 管理端 RBAC（模块级权限 + 敏感操作独立授权）
- 跨语言契约测试（仓外裁判 + fixture 对拍）

### 可复用库
- [spring-data-permission-starter](https://github.com/StevenTsai/spring-data-permission-starter) — 数据权限的 Spring Boot Starter 实现，可直接导入使用

### 生产验证案例（Case Studies）
- [MTO 工作流引擎](case-studies/mto-workflow-engine/) — 从珠宝 ERP 提取的通用工单状态机，可复用到服装/家具/电子加工等行业
- [Spring Events 跨模块联动](case-studies/spring-events-decoupling/) — 事件驱动解耦：发货→应收 / 工单→库存 / 质检→返工
- [计件工资计算引擎](case-studies/piecework-wage-engine/) — Pipeline 模式的计件工资引擎，支持难度系数和质量系数
- [生产护栏：fail-fast 启动守卫 + 可插拔状态存储](case-studies/production-guardrails/) — 生产中间件 2→1、回切零代码、契约测试补齐（源自 IoT 后端，非珠宝 ERP）

### 架构决策记录（6 个）
- Session vs JWT、MyBatis vs JPA、逻辑删除 vs 物理删除
- [Spring Boot 单轨 vs NestJS 计划](decisions/why-spring-boot-not-nestjs.md) — 框架替换的四条机制性收益，非偏好叙事
- [状态存储进 MySQL，Redis 可插拔](decisions/why-redis-optional.md) — 生产中间件 2→1，回切零代码
- [客户端零构建链：JS 而非 TS](decisions/why-javascript-not-typescript.md) — 代价已兑现的诚实记录与根因修正

### 教程与指南
- **7 步从零构建教程**：从领域模型到合规检查的完整叙事线
- **4 个实操指南**：快速开始、扩展新模块、定制权限、Spring Boot 3 迁移

## 生产验证来源

Case Study 的代码从一个**真实生产系统**（珠宝加工 ERP）中提取并抽象：

| 维度 | 数据 |
|------|------|
| API 端点 | 152 个 |
| 数据库表 | 44 张 |
| 测试用例 | 909 个 |
| 技术栈 | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 |
| 业务模块 | 生产、销售、财务、库存、质检、基础数据 |

> 源项目为私有仓库，仅提取通用架构模式，不包含业务敏感配置和客户数据。

第四个案例 [production-guardrails](case-studies/production-guardrails/) 提取自**另一个私有生产系统**（IoT 设备管理后端，电池遥测域）：约 60 万注册用户的容量口径、24 个 REST 端点、155 个单测。同样只提取通用架构模式。

## 适合谁看

| 角色 | 你能得到什么 |
|------|------------|
| **后端架构师** | 数据权限、工单状态机、事件驱动解耦等通用模式的落地方案 |
| **中级 Java 开发者** | "不只是 CRUD"的业务系统怎么写，以及如何设计 Starter 库 |
| **技术负责人** | 技术选型的 trade-off 分析（ADR） |
| **需要数据权限的开发者** | 直接使用 [spring-data-permission-starter](https://github.com/StevenTsai/spring-data-permission-starter) |

## 文档结构

```
├── distribution-starter/            最小可运行示例（clone 后直接跑）
│   ├── src/main/java/               Spring Boot + MyBatis 完整骨架
│   ├── src/main/script/sql/         数据库初始化脚本
│   └── README.md                    快速开始指南
│
├── architecture/                    设计模式（核心价值）
│   ├── data-permission-model.md     4 级数据权限：SQL 层注入的行级过滤
│   ├── commission-pipeline.md       佣金流转：事件驱动状态机 + 规则快照 + 冲回
│   ├── audit-and-compliance.md      审计日志：JSON 快照 + 统一入口
│   ├── mybatis-patterns.md          MyBatis 工程化：三段式 SQL 片段 + 对偶查询
│   ├── pii-encryption.md            PII 加密：AES-GCM + HMAC 检索键 + 统一脱敏
│   ├── admin-rbac.md                管理端 RBAC：模块级权限 + 敏感操作独立授权
│   └── cross-language-contract-testing.md  跨语言契约测试：仓外裁判 + fixture 对拍
│
├── decisions/                       架构决策记录（ADR）
│   ├── why-session-not-jwt.md       为什么选 Session Token
│   ├── why-mybatis-not-jpa.md       为什么选 MyBatis
│   ├── why-logical-delete.md        为什么用逻辑删除
│   ├── why-spring-boot-not-nestjs.md  为什么后端收敛为 Spring Boot 单轨
│   ├── why-redis-optional.md        状态存储进 MySQL 表，Redis 可插拔回切
│   └── why-javascript-not-typescript.md  客户端零构建链优先（代价已兑现的诚实记录）
│
├── guides/                          实操指南
│   ├── quick-start.md               5 分钟跑起来
│   ├── extend-new-module.md         如何扩展新模块
│   ├── customize-permission.md      如何定制权限模型
│   └── spring-boot-3-migration.md   Spring Boot 3.x 迁移指南
│
├── case-studies/                    生产验证案例（提取自多个私有生产系统）
│   ├── mto-workflow-engine/         MTO 工作流引擎
│   │   ├── WorkOrderStateMachine    9 态工单状态机
│   │   ├── ProcessRouteConfig       YAML 工序路由配置
│   │   └── MaterialCheckService     物料齐套检查
│   ├── spring-events-decoupling/    Spring Events 跨模块联动
│   │   ├── DeliveryCompleteEvent    发货→应收账款
│   │   ├── WorkOrderFinishedEvent   完工→成品入库
│   │   └── QualityDefectEvent       质检→返工工单
│   ├── piecework-wage-engine/       计件工资计算引擎
│   │   ├── WageCalculator           工资计算引擎
│   │   ├── WageSettlementPipeline   Pipeline 编排器
│   │   └── 4 个 Pipeline Stage      计算→调整→确认→汇总
│   └── production-guardrails/       生产护栏（fail-fast 启动守卫 + 可插拔状态存储）
│       ├── StartupGuard             配置红线拒绝启动矩阵
│       ├── StateStore               六原语契约 + 三实现（mysql/redis/memory）
│       └── 契约测试套件              40 用例零外部服务（H2 MODE=MySQL）
│
├── CONTRIBUTING.md                  贡献指南
│
└── tutorial/                        从零构建系列（推荐阅读顺序）
    ├── 01-domain-model.md           领域模型：核心实体与关系
    ├── 02-data-permission.md        数据权限：权限解析与 SQL 注入
    ├── 03-crud-scaffolding.md       CRUD 脚手架：Mapper → Service → Controller
    ├── 04-audit-logging.md          审计日志：统一入口与 JSON 快照
    ├── 05-commission-flow.md        佣金流转：事件推进与规则匹配
    ├── 06-settlement.md             结算流程：归集 → 审核 → 打款
    └── 07-compliance.md             合规检查：审查流程与业务嵌入
```

### 可复用库（独立仓库）

| 库 | 说明 | 仓库 |
|---|------|------|
| `spring-data-permission-starter` | 数据权限 Spring Boot Starter，可直接导入使用 | [GitHub](https://github.com/StevenTsai/spring-data-permission-starter) |

## 阅读建议

**如果你想跑起来看看**：进入 `distribution-starter/` 目录，按 README.md 的步骤 5 分钟启动。

**如果你想快速了解**：先读 `architecture/` 下的 4 篇设计模式文档。

**如果你想看生产验证案例**：进入 `case-studies/` 目录，每个案例都有 README 和演进文档。

**如果你想从头理解**：按 `tutorial/` 的 01→07 顺序阅读，这是从零构建的完整叙事线。

**如果你要做技术选型**：读 `decisions/` 下的 6 篇 ADR，看 trade-off 分析。

**如果你要基于此做二次开发**：读 `guides/` 下的实操指南。

## 核心设计理念

### 1. 数据权限下沉到 SQL 层

不在每个 Service 方法中手动过滤数据，而是通过 MyBatis 的 `<sql>` 片段将权限条件自动注入 SQL。开发者只需要选择"用带权限的查询"还是"不带权限的查询"。

### 2. 事件驱动而非状态驱动

业务单的状态变更通过"事件"推进，而非直接修改状态。每个事件自动触发：状态更新 → 佣金计算 → 线索联动 → 审计日志，全部在同一个事务中。

### 3. 规则快照而非引用

佣金流水保存的是"当时的规则是什么"（JSON 快照），而非"规则 ID 是什么"。这样即使政策变更，已生成的佣金仍然可追溯。

### 4. 冲回而非删除

业务单取消时，已生成的佣金不删除，而是生成负数流水。已结算的佣金生成挂账，下期抵扣。符合财务系统"红字冲销"的惯例。

### 5. 审计日志与合规记录分离

审计日志是"被动记录"（所有操作自动记录），合规记录是"主动管理"（需要审批流程）。两者互补，满足不同维度的追溯需求。

## Case Study 抽象方法论

从珠宝 ERP 提取通用模式的四个原则：

| 原则 | 说明 | 示例 |
|------|------|------|
| **识别特化锚点** | 找出明显只属于当前业务的字段/逻辑 | 金料/石料 → `InventoryQueryService` 接口 |
| **接口隔离变化** | 用接口封装可变部分 | 17 道固定工序 → YAML 配置路由 |
| **保留模式替换细节** | 保留架构模式，替换业务细节 | 状态机模式保留，具体状态值替换 |
| **先工作再通用** | 先让它跑起来，验证后再抽象 | 先实现 `piecePrice × qty`，再加系数 |

## 技术栈

- **框架**：Spring Boot 3.2.5
- **语言**：Java 17
- **ORM**：MyBatis 3.0.3
- **数据库**：MySQL 8.0
- **缓存**：Redis
- **API 文档**：SpringDoc 2.3.0（OpenAPI 3）

## License

[Apache License 2.0](LICENSE)

---

## English Documentation

English translations of the core documents are available in the [`en/`](en/) directory:

| Document | Description |
|----------|-------------|
| [en/README.md](en/README.md) | Full project overview |
| [en/preface.md](en/preface.md) | Preface: what this book is and where the patterns come from |
| [en/architecture/data-permission-model.md](en/architecture/data-permission-model.md) | Row-Level Data Permission Model |
| [en/architecture/commission-pipeline.md](en/architecture/commission-pipeline.md) | Commission Pipeline: State-Machine-Driven Flow |
| [en/architecture/audit-and-compliance.md](en/architecture/audit-and-compliance.md) | Audit Snapshot: Audit Log and Compliance Record Pattern |
| [en/architecture/mybatis-patterns.md](en/architecture/mybatis-patterns.md) | MyBatis Engineering: SQL Fragments and Permission Injection |
| [en/architecture/pii-encryption.md](en/architecture/pii-encryption.md) | PII Encryption: AES-GCM, HMAC Lookup Keys, Masking |
| [en/architecture/admin-rbac.md](en/architecture/admin-rbac.md) | Admin RBAC: Module-Level Permissions |
| [en/architecture/cross-language-contract-testing.md](en/architecture/cross-language-contract-testing.md) | Cross-Language Contract Testing |
| [en/decisions/why-javascript-not-typescript.md](en/decisions/why-javascript-not-typescript.md) | ADR: Zero Build Chain — JavaScript over TypeScript |
| [en/decisions/why-logical-delete.md](en/decisions/why-logical-delete.md) | ADR: Why Logical Delete |
| [en/decisions/why-mybatis-not-jpa.md](en/decisions/why-mybatis-not-jpa.md) | ADR: Why MyBatis over JPA |
| [en/decisions/why-redis-optional.md](en/decisions/why-redis-optional.md) | ADR: State Storage in MySQL, Redis Optional |
| [en/decisions/why-session-not-jwt.md](en/decisions/why-session-not-jwt.md) | ADR: Why Session Token over JWT |
| [en/decisions/why-spring-boot-not-nestjs.md](en/decisions/why-spring-boot-not-nestjs.md) | ADR: Spring Boot Single Track over NestJS |
| [en/guides/quick-start.md](en/guides/quick-start.md) | Quick Start Guide |
| [en/guides/extend-new-module.md](en/guides/extend-new-module.md) | Extending a New Module |
| [en/guides/customize-permission.md](en/guides/customize-permission.md) | Customizing the Permission Model |
| [en/guides/spring-boot-3-migration.md](en/guides/spring-boot-3-migration.md) | Spring Boot 3.x Migration Guide |
| [en/tutorial/](en/tutorial/) | Build-from-scratch tutorial, steps 01–07 |
| [en/case-studies/](en/case-studies/) | Case-study READMEs and evolution notes |
| [en/CONTRIBUTING.md](en/CONTRIBUTING.md) | Contributing guide |
| [en/SECURITY.md](en/SECURITY.md) | Security policy |
| [en/distribution-starter/README.md](en/distribution-starter/README.md) | Starter quick start and project structure |

The English directory covers every book page except the VitePress homepage (`index.md`); the internal `doc/open-source-strategy.md` remains Chinese-only.

For Spring Boot 2.7 users, see the [migration guide](guides/spring-boot-3-migration.md) (also available in [English](en/guides/spring-boot-3-migration.md)).
