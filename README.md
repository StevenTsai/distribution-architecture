# Distribution Architecture

一套经过生产验证的分销管理系统架构设计文档，展示了数据权限、佣金流转、审计合规等通用模式在 Spring Boot + MyBatis 技术栈下的工程化落地方案。

## 这是什么

这是一套**设计文档** + **最小可运行示例**，记录了一个分销管理系统从零构建的完整过程，包含：

- **4 个核心设计模式**：数据权限、佣金流转、审计日志、MyBatis 工程化
- **3 个架构决策记录**：Session vs JWT、MyBatis vs JPA、逻辑删除 vs 物理删除
- **7 步从零构建教程**：从领域模型到合规检查的完整叙事线
- **3 个实操指南**：快速开始、扩展新模块、定制权限

## 适合谁看

| 角色 | 你能得到什么 |
|------|------------|
| **后端架构师** | 数据权限、佣金流转、审计日志等通用模式的落地方案 |
| **中级 Java 开发者** | "不只是 CRUD"的业务系统怎么写 |
| **技术负责人** | 技术选型的 trade-off 分析（ADR） |
| **创业团队 CTO** | 分销系统的领域模型，缩短自研周期 |

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
│   └── mybatis-patterns.md          MyBatis 工程化：三段式 SQL 片段 + 对偶查询
│
├── decisions/                       架构决策记录（ADR）
│   ├── why-session-not-jwt.md       为什么选 Session Token
│   ├── why-mybatis-not-jpa.md       为什么选 MyBatis
│   └── why-logical-delete.md        为什么用逻辑删除
│
├── guides/                          实操指南
│   ├── quick-start.md               5 分钟跑起来
│   ├── extend-new-module.md         如何扩展新模块
│   ├── customize-permission.md      如何定制权限模型
│   └── spring-boot-3-migration.md   Spring Boot 3.x 迁移指南
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

## 阅读建议

**如果你想跑起来看看**：进入 `distribution-starter/` 目录，按 README.md 的步骤 5 分钟启动。

**如果你想快速了解**：先读 `architecture/` 下的 4 篇设计模式文档。

**如果你想从头理解**：按 `tutorial/` 的 01→07 顺序阅读，这是从零构建的完整叙事线。

**如果你要做技术选型**：读 `decisions/` 下的 3 篇 ADR，看 trade-off 分析。

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

## 技术栈

- **框架**：Spring Boot 2.7.18
- **语言**：Java 8
- **ORM**：MyBatis 2.3.1
- **数据库**：MySQL 8.0
- **缓存**：Redis

## License

[Apache License 2.0](LICENSE)

---

## English Summary

**Distribution Architecture** is a set of production-validated design documents for building a B2B distribution management system using Spring Boot + MyBatis. It includes architecture patterns, decision records, and a runnable starter project.

### What's Included

| Component | Description |
|-----------|-------------|
| [distribution-starter/](distribution-starter/) | Minimal runnable Spring Boot project with 8 database tables |
| [architecture/](architecture/) | 4 core design patterns with code examples |
| [decisions/](decisions/) | 3 Architecture Decision Records (ADRs) |
| [tutorial/](tutorial/) | 7-step build-from-scratch guide |
| [guides/](guides/) | Practical guides including Spring Boot 3.x migration |

### Core Design Patterns

1. **Row-Level Data Permissions** — SQL-layer filtering via MyBatis `<sql>` fragments, zero business code intrusion
2. **Event-Driven Commission Pipeline** — State machine + rule snapshots + automatic reversal entries
3. **Audit Logging** — JSON before/after snapshots with unified entry point
4. **MyBatis Engineering** — 3-segment SQL fragments (BaseColumnList / ConditionWhere / ScopeCondition)

### Quick Start

```bash
cd distribution-starter
# Create MySQL database
mysql -u root -p -e "CREATE DATABASE distribution_starter DEFAULT CHARSET utf8mb4;"
# Initialize tables
mysql -u root -p distribution_starter < src/main/script/sql/01_shared_tables.sql
mysql -u root -p distribution_starter < src/main/script/sql/02_distribution_tables.sql
# Update DB credentials in application-dev.properties
# Start the application
mvn spring-boot:run
# Access Swagger UI: http://localhost:9030/swagger-ui.html
```

### Tech Stack

- Spring Boot 2.7.18 / Java 8 / MyBatis 2.3.1 / MySQL 8.0
- Spring Boot 3.x migration guide available at [guides/spring-boot-3-migration.md](guides/spring-boot-3-migration.md)

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### License

[Apache License 2.0](LICENSE)
