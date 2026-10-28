# 关于本书

## 这是什么

这是一本关于**企业级架构模式与方法论**的实战书籍。所有模式来自真实生产系统与公开参考实现的提炼与抽象——编程语言只是证据的载体，真正沉淀的是判断、取舍与方法。

与市面架构书籍不同，本书的特点是：

| 特点 | 说明 |
|------|------|
| **生产验证** | 模式来自真实项目（珠宝 ERP、IoT 后端）与公开参考实现（GroundedRAG），不是教科书示例 |
| **诚实记录** | 技术决策不仅讲优点，还诚实记录已兑现的代价 |
| **可运行代码** | 4 个案例研究包含完整测试（124 个测试全部通过） |
| **演进视角** | 展示从"行业特定"到"通用模式"的抽象过程 |

## 读者是谁

- **后端架构师**：寻找可落地的架构模式参考
- **中级开发者**（Java 或其他语言）：想理解"不只是 CRUD"的业务系统怎么写
- **技术负责人**：需要技术选型的 trade-off 分析
- **创业团队**：从零构建企业级系统时的参考指南

## 如何阅读

### 如果你想快速了解

先读 [第一部分：基础模式](/architecture/data-permission-model)，这 4 个模式是后续佣金、结算与合规章节的共同底座。

### 如果你准备做技术选型

直接看 [第三部分：技术决策](/decisions/why-session-not-jwt)，每篇 ADR 都有完整的 trade-off 分析。

### 如果你想动手实践

按 [第五部分：从零构建](/tutorial/01-domain-model) 的顺序，7 步完成一个完整的分销系统。

### 如果你有特定场景需求

浏览 [第四部分：实战案例](/case-studies/mto-workflow-engine/)，找到与你场景类似的案例。

### 如果你关心 AI 系统的输出可靠性

先读 [AI Agent 架构](/architecture/agent-architecture-patterns)，再从 [AI 工程实践](/playbooks/ai-engineering/)进入结构化输出、多模型路由和长文档生成；最后读 [组件正确 ≠ 系统有效](/architecture/verifier-failure-modes)与[声明级校验门](/architecture/claim-level-verifier)。开源前的提取纪律见[开源发布止血带](/playbooks/open-source-compliance)。

### 如果你要落地 DDD 或企业架构

先读 [TOGAF 实践](/playbooks/enterprise-architecture/togaf-in-practice)，再读 [DDD 七步落地](/playbooks/enterprise-architecture/ddd-seven-step-landing)，最后接入[架构组合治理](/playbooks/system-portfolio-governance/)。

## 内容来源

本书的架构模式来自两个真实生产系统；AI 系统可靠性章节来自一个公开参考实现，AI Agent 架构与 AI 工程实践章节来自高风险 AI 应用的实际工程结构；实战案例中的 [OncoHub 架构演进](/case-studies/oncohub-architecture-evolution)与接手工程的三轮安全审计深水区，素材取自作者已公开发表的个人项目复盘：

> 各架构章节标题下方标注「验证入口 / 证据边界」：有可运行实现的给出测试入口，没有的明确写出来源与边界，避免把单团队经验当作行业结论。

### 系统一：珠宝加工 ERP

| 维度 | 数据 |
|------|------|
| API 端点 | 152 个 |
| 数据库表 | 44 张 |
| 测试用例 | 909 个 |
| 技术栈 | Java 17 + Spring Boot 3.2 + MyBatis-Plus |
| 业务模块 | 生产、销售、财务、库存、质检、基础数据 |

### 系统二：IoT 设备管理后端

| 维度 | 数据 |
|------|------|
| 注册用户容量 | ~60 万 |
| REST 端点 | 24 个 |
| 单元测试 | 155 个 |
| 技术栈 | Java 17 + Spring Boot 3.2 |

> 源项目为私有仓库，仅提取通用架构模式，不包含业务敏感配置和客户数据。

### 参考实现：GroundedRAG（公开）

| 维度 | 数据 |
|------|------|
| 定位 | 声明级校验门 RAG 参考实现（本书 AI 系统可靠性章节的证据源） |
| 许可 | MIT · [github.com/StevenTsai/grounded-rag](https://github.com/StevenTsai/grounded-rag) |
| 测试 | 247 个单元测试，覆盖率 95%（`v1.0.0`） |
| 证据档位 | **提炼参考实现**：仓库内为合成数据，真实评测集本地维护、不分发、可应请求供审阅 |

> 前两个系统为私有仓库，仅提取通用架构模式；GroundedRAG 为公开仓库，可 clone 复现。声明级校验门、双口径评测等可复现量化证据 pin 至 `v1.0.0`。AI Agent 架构与 AI 工程实践相关的私有业务代码、提示词和运营数据未公开，页面中的实现与阈值均已泛化，不构成行业基准。OncoHub 案例与安全审计深水区的素材首发于个人主页（2026-04-11 / 2026-09-18），本文为其知识库改写版，业务数据未收录。

## 开源许可

本书内容基于 [Apache License 2.0](https://github.com/StevenTsai/distribution-architecture/blob/main/LICENSE) 开源。

欢迎提交 Issue 和 Pull Request 改进内容。

## 联系作者

- GitHub: [@StevenTsai](https://github.com/StevenTsai)
- 项目仓库: [distribution-architecture](https://github.com/StevenTsai/distribution-architecture)
