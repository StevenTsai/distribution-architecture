# Distribution Architecture

[![Deploy Docs](https://github.com/StevenTsai/distribution-architecture/actions/workflows/deploy.yml/badge.svg)](https://github.com/StevenTsai/distribution-architecture/actions/workflows/deploy.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

中文 | [English](en/README.md)

📖 **[在线阅读《架构炼金术：从真实系统提炼可复用的架构模式》](https://steventsai.github.io/distribution-architecture/)** | 企业级架构模式与方法论库，从真实生产系统中提炼（生产实证 + 跨语言交付 + 架构治理 + AI 系统验证）。

## 这是什么

这是一套**架构设计文档** + **可复用 Starter 库** + **生产验证案例** + **全生命周期方法论**，覆盖一个企业级系统从构建、选型、验证，到接手、治理、运维的完整过程。

代码之外，这里还沉淀了七套可以直接套用的方法：**接手工程**（进入时该不该接、投多少、何时停）、**组合治理**（持有期哪些该关、哪些该治）、**生产运维**（运行期怎么观测、怎么变更、怎么救火）、**AI 工程实践**（生成链路怎么控制失败）、**企业架构实践**（领域边界怎么进入代码）、**工程一致性规范**（团队共用的命名、接口、发布与术语基线）、**开源提取纪律**（私有模式怎么安全地进公开仓库）。

> 我在生产系统中遇到了数据权限、工作流引擎、事件驱动解耦、计件工资等通用问题。
> 我先在真实项目中用 Spring Boot + MyBatis 实现了这些模式，
> 然后总结为设计文档和 ADR，
> 最后抽象为可复用的组件并开源。

### 设计模式与架构方法（9 篇）
- 数据权限下沉到 SQL 层
- 事件驱动状态机（佣金流转）
- 审计日志 JSON 快照
- MyBatis 工程化
- PII 加密与脱敏（AES-GCM + HMAC 检索键）
- 管理端 RBAC（模块级权限 + 敏感操作独立授权）
- 跨语言契约测试（仓外裁判 + fixture 对拍）
- [单元化架构](architecture/cell-based-architecture.md) — 爆炸半径、单元键、数据分片与「拔电验证」
- [服务治理的三种形态](architecture/service-governance-forms.md) — SDK / Agent / Sidecar 的取舍矩阵

### AI Agent 架构（1 篇）
- [AI Agent 架构：声明式与状态机](architecture/agent-architecture-patterns.md) — 按风险分层、代码守卫、聚合接口与流式边界

### AI 系统可靠性（2 篇，证据来自开源参考实现 [GroundedRAG](https://github.com/StevenTsai/grounded-rag)）
- [组件正确 ≠ 系统有效](architecture/verifier-failure-modes.md) — 离线校验 100% vs 端到端 30–46% 的失败复盘与双口径评测制度
- [声明级校验门](architecture/claim-level-verifier.md) — 把不可信输出降维成可判定断言：四步门、同源不变量、单调权限

### AI 工程实践（3 篇）
- [结构化输出治理](playbooks/ai-engineering/structured-output-governance.md) — 截断、提取、有限修复、结构校验与业务不变量
- [多模型路由与故障隔离](playbooks/ai-engineering/model-routing-and-resilience.md) — 模型目录、Provider 隔离、熔断、重试预算与可观测性
- [长文档多阶段生成](playbooks/ai-engineering/long-document-pipeline.md) — 分段审阅、可审查改写、全文重组与局部重试

### 企业架构实践（2 篇）
- [DDD 七步落地](playbooks/enterprise-architecture/ddd-seven-step-landing.md) — 从统一语言、限界上下文到聚合、不变量和领域事件
- [TOGAF 实践](playbooks/enterprise-architecture/togaf-in-practice.md) — BA/DA/AA/TA、端到端主线、事件优先需求与轻量架构评估

### 可复用库
- [spring-data-permission-starter](https://github.com/StevenTsai/spring-data-permission-starter) — 数据权限的 Spring Boot Starter 实现，可直接导入使用
- [grounded-rag](https://github.com/StevenTsai/grounded-rag) — 声明级校验门 RAG 参考实现（Python / MIT），本书 AI 系统可靠性章节的证据源

### 生产验证案例（4 个代码案例，124 个测试 + 1 篇架构演进复盘）
- [MTO 工作流引擎](case-studies/mto-workflow-engine/) — 从珠宝 ERP 提取的通用工单状态机，可复用到服装/家具/电子加工等行业
- [Spring Events 跨模块联动](case-studies/spring-events-decoupling/) — 事件驱动解耦：发货→应收 / 工单→库存 / 质检→返工
- [计件工资计算引擎](case-studies/piecework-wage-engine/) — Pipeline 模式的计件工资引擎，支持难度系数和质量系数
- [生产护栏：fail-fast 启动守卫 + 可插拔状态存储](case-studies/production-guardrails/) — 生产中间件 2→1、回切零代码、契约测试补齐（源自 IoT 后端，非珠宝 ERP）
- [OncoHub 架构演进](case-studies/oncohub-architecture-evolution.md) — 一份设计文档改了 8 次：读代码纠假设、认证解耦、数据流向与灰度四阶段（无配套代码，叙事案例）

### 架构决策记录（6 个）
- Session vs JWT、MyBatis vs JPA、逻辑删除 vs 物理删除
- [Spring Boot 单轨 vs NestJS 计划](decisions/why-spring-boot-not-nestjs.md) — 框架替换的四条机制性收益，非偏好叙事
- [状态存储进 MySQL，Redis 可插拔](decisions/why-redis-optional.md) — 生产中间件 2→1，回切零代码
- [客户端零构建链：JS 而非 TS](decisions/why-javascript-not-typescript.md) — 代价已兑现的诚实记录与根因修正

### 教程与指南
- **7 步从零构建教程**：从领域模型到合规检查的完整叙事线
- **4 个实操指南**：快速开始、扩展新模块、定制权限、Spring Boot 3 迁移

### 方法论 Playbooks（7 套）
- [接手工程](playbooks/takeover-engineering/) — 进入时该不该接、投多少、何时停
- [架构组合治理](playbooks/system-portfolio-governance/) — 系统群清点、诊断、关停并转与防回潮
- [生产运维](playbooks/production-operations/) — 观测、变更、故障响应与演练
- [AI 工程实践](playbooks/ai-engineering/) — 结构化输出、多模型路由与长文档多阶段生成
- [企业架构实践](playbooks/enterprise-architecture/) — DDD 落地与企业架构方法裁剪
- [工程一致性规范](playbooks/engineering-consistency-standards.md) — 命名、接口、发布与术语基线
- [开源发布止血带](playbooks/open-source-compliance.md) — 私有生产到公开模式库的提取纪律：切分线、扫描器开源/词表闭源、推送前清单（含 Git 历史与凭据轮换）

## 生产验证来源

Case Study 的代码从一个**真实生产系统**（珠宝加工 ERP）中提取并抽象：

| 维度 | 数据 |
|------|------|
| API 端点 | 152 个 |
| 数据库表 | 44 张 |
| 测试用例 | 909 个 |
| 技术栈 | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 |
| 业务模块 | 生产、销售、财务、库存、质检、基础数据 |

> 源项目为私有仓库，仅提取通用架构模式，不包含业务敏感配置和客户数据。表中的 909 个测试是源系统口径；本仓库 4 个案例可运行的测试为 124 个。

第四个案例 [production-guardrails](case-studies/production-guardrails/) 提取自**另一个私有生产系统**（IoT 设备管理后端，电池遥测域）：约 60 万注册用户的容量口径、24 个 REST 端点、155 个源系统单测；提取到本仓库后的护栏案例为 51 个测试。同样只提取通用架构模式。

AI 系统可靠性章节的证据源是一个**公开参考实现** [grounded-rag](https://github.com/StevenTsai/grounded-rag)（MIT）：247 个单元测试、覆盖率 95%；确定性评测集三指标 1.0，端到端期望匹配率 30.8%–46.2%——两个口径成对引用（详见[失败复盘](architecture/verifier-failure-modes.md)）。真实评测集本地维护、不分发，仓库内为合成数据。

AI Agent 架构与 AI 工程实践提炼自高风险 AI 应用和长文档任务的实际工程结构；相关业务代码、提示词和运营数据未公开，文中的阈值与实现示例均已泛化，不构成行业基准。

## 适合谁看

| 角色 | 你能得到什么 |
|------|------------|
| **后端架构师** | 数据权限、工单状态机、事件驱动解耦等通用模式的落地方案 |
| **中级 Java 开发者** | "不只是 CRUD"的业务系统怎么写，以及如何设计 Starter 库 |
| **技术负责人** | 技术选型的 trade-off 分析（ADR），以及系统群的治理与关停并转决策（[架构组合治理](playbooks/system-portfolio-governance/)） |
| **售前 / 商务 / 决策者** | [接手工程工具箱](playbooks/takeover-engineering/)：接手前怎么评估、怎么报价、什么时候止损 |
| **需要数据权限的开发者** | 直接使用 [spring-data-permission-starter](https://github.com/StevenTsai/spring-data-permission-starter) |

## 文档结构

```
├── distribution-starter/            最小可运行示例（初始化 MySQL 后启动）
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
│   ├── cross-language-contract-testing.md  跨语言契约测试：仓外裁判 + fixture 对拍
│   ├── cell-based-architecture.md   单元化架构：爆炸半径、单元键、数据分片与故障演练
│   ├── service-governance-forms.md  服务治理三形态：SDK / Agent / Sidecar 的取舍矩阵
│   ├── agent-architecture-patterns.md  AI Agent 架构：声明式、状态机与代码守卫
│   ├── verifier-failure-modes.md    组件正确 ≠ 系统有效：校验门端到端失败复盘（AI）
│   └── claim-level-verifier.md      声明级校验门：可判定断言 + 四步门 + 两条不变量（AI）
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
│   ├── production-guardrails/       生产护栏（fail-fast 启动守卫 + 可插拔状态存储）
│   │   ├── StartupGuard             配置红线拒绝启动矩阵
│   │   ├── StateStore               六原语契约 + 三实现（mysql/redis/memory）
│   │   └── 契约测试套件              51 用例零外部服务（H2 MODE=MySQL）
│   └── oncohub-architecture-evolution.md  架构演进复盘：设计文档改 8 次（叙事案例）
│
├── playbooks/                       方法论 playbook（架构师全周期实践）
│   └── takeover-engineering/        接手工程工具箱
│       ├── README.md                导言：与主仓库的关系 + 三层用法
│       ├── 00-methodology.md        方法论总纲：四原则 + 六阶段 + 止损闸门
│       ├── 01-investment-discipline.md  签约前投入纪律：L0–L3 封顶 + 杠杆对称
│       ├── case-walkthrough.md      附录：虚构案例串跑（教学示例，走完六阶段）
│       ├── deep-dives/legacy-security-audit.md  三轮安全审计：46 项发现与限频自噬
│       ├── templates/               可直接填写的模板 01–10
│       └── hooks/21-questions.md    接手前必须问清的 21 个问题（免费钩子）
│
├── playbooks/system-portfolio-governance/  架构组合治理（姊妹篇：系统群怎么管）
│   ├── 00-methodology.md            四阶段：清点 → 诊断 → 决策 → 执行与防回潮
│   ├── 05-architecture-review.md    架构评审机制：三级评审 + 一页纸 + 例外台账
│   └── templates/                   组合清单 / 诊断卡 / 决策记录 / 路线图与闸门
│
├── playbooks/ai-engineering/        AI 工程实践
│   ├── README.md                    生成链路可控性总览
│   ├── structured-output-governance.md  结构化输出五层失败边界
│   ├── model-routing-and-resilience.md  模型目录、Provider 隔离与故障降级
│   └── long-document-pipeline.md    分段审阅、可审查改写与全文重组
│
├── playbooks/enterprise-architecture/  企业架构实践
│   ├── README.md                    企业架构方法总览
│   ├── ddd-seven-step-landing.md    从领域语言到聚合代码
│   └── togaf-in-practice.md         BA/DA/AA/TA 与方法裁剪
│
├── playbooks/engineering-consistency-standards.md  工程一致性规范（团队可直接套用）
│
├── playbooks/open-source-compliance.md  开源发布止血带（提取纪律 + 推送前清单）
│
├── playbooks/production-operations/  生产运维（第三姊妹篇：系统运行期怎么稳住）
│   ├── 00-methodology.md            五原则 + 四能力域（观测/变更/响应/演练）
│   ├── deep-dives/                  全链路灰度：标记透传、消息灰度、数据兼容
│   └── templates/                   告警规范 / 变更与灰度 / 故障复盘 / 容量与演练
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

**如果你想跑起来看看**：进入 `distribution-starter/` 目录，按 README.md 的步骤初始化 MySQL 并启动。

**如果你想快速了解**：先读 `architecture/` 下的 4 篇基础模式（数据权限、事件驱动、审计、MyBatis），再看其余 6 篇。

**如果你想看生产验证案例**：进入 `case-studies/` 目录，每个案例都有 README 和演进文档。

**如果你想从头理解**：按 `tutorial/` 的 01→07 顺序阅读，这是从零构建的完整叙事线。

**如果你在做 AI 系统**：先读[AI Agent 架构](architecture/agent-architecture-patterns.md)，再从[AI 工程实践](playbooks/ai-engineering/)进入结构化输出、多模型路由和长文档生成；输出可靠性继续读[组件正确 ≠ 系统有效](architecture/verifier-failure-modes.md)与[声明级校验门](architecture/claim-level-verifier.md)。

**如果你要做技术选型**：读 `decisions/` 下的 6 篇 ADR，看 trade-off 分析。平台层选型看[服务治理的三种形态](architecture/service-governance-forms.md)；容量到顶或要缩小故障域时读[单元化架构](architecture/cell-based-architecture.md)。

**如果你要接手别人的系统**：读 `playbooks/takeover-engineering/`，从 [21 个问题](playbooks/takeover-engineering/hooks/21-questions.md)开始，再按需取用 10 份模板。

**如果你要治理自己的系统群**：读 `playbooks/system-portfolio-governance/`，从[方法论总纲](playbooks/system-portfolio-governance/00-methodology.md)开始——清点、诊断、关停并转决策、防回潮闸门。团队工程基线见[工程一致性规范](playbooks/engineering-consistency-standards.md)。

**如果你要建立企业架构方法或落地 DDD**：先读[TOGAF 实践](playbooks/enterprise-architecture/togaf-in-practice.md)，再读[DDD 七步落地](playbooks/enterprise-architecture/ddd-seven-step-landing.md)，最后接入组合治理与架构评审。

**如果你要管生产环境的稳定运行**：读 `playbooks/production-operations/`，从[运维方法论总纲](playbooks/production-operations/00-methodology.md)开始——可观测、变更与灰度、故障响应与复盘、容量与演练。人少的话直接看「最小可行运维」一节。

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

开源侧的提取纪律（切分线怎么画、泄露扫描、推送前清单）单独沉淀为[开源发布止血带](playbooks/open-source-compliance.md)。

## 技术栈

- **框架**：Spring Boot 3.2.5
- **语言**：Java 17
- **ORM**：MyBatis 3.0.3
- **数据库**：MySQL 8.0
- **缓存**：Redis（可选）
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
| [en/architecture/cell-based-architecture.md](en/architecture/cell-based-architecture.md) | Cell-Based Architecture: Confining the Blast Radius |
| [en/architecture/service-governance-forms.md](en/architecture/service-governance-forms.md) | Three Forms of Service Governance: SDK, Agent, Sidecar |
| [en/playbooks.md](en/playbooks.md) | Methodology playbooks index (the playbooks themselves are Chinese-only) |
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

The English directory covers every book page except the VitePress homepage (`index.md`) and the AI architecture and engineering articles (added 2026-10, Chinese-only for now). The seven methodology playbooks are Chinese-only by design; [en/playbooks.md](en/playbooks.md) indexes what they cover and links to each one. The internal `doc/open-source-strategy.md` also remains Chinese-only.

For Spring Boot 2.7 users, see the [migration guide](guides/spring-boot-3-migration.md) (also available in [English](en/guides/spring-boot-3-migration.md)).
