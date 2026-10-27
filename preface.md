# 关于本书

## 这是什么

这是一本关于 **Java 企业级架构模式** 的实战书籍，所有内容来自真实生产系统的提炼和抽象。

与市面架构书籍不同，本书的特点是：

| 特点 | 说明 |
|------|------|
| **生产验证** | 每个模式都来自真实项目（珠宝 ERP + IoT 后端），不是教科书示例 |
| **诚实记录** | 技术决策不仅讲优点，还诚实记录已兑现的代价 |
| **可运行代码** | 4 个案例研究包含完整测试（124 个测试全部通过） |
| **演进视角** | 展示从"行业特定"到"通用模式"的抽象过程 |

## 读者是谁

- **后端架构师**：寻找可落地的架构模式参考
- **中级 Java 开发者**：想理解"不只是 CRUD"的业务系统怎么写
- **技术负责人**：需要技术选型的 trade-off 分析
- **创业团队**：从零构建企业级系统时的参考指南

## 如何阅读

### 如果你想快速了解

先读 [第一部分：基础模式](/architecture/data-permission-model)，这 4 个模式覆盖了 80% 的企业级需求。

### 如果你准备做技术选型

直接看 [第三部分：技术决策](/decisions/why-session-not-jwt)，每篇 ADR 都有完整的 trade-off 分析。

### 如果你想动手实践

按 [第五部分：从零构建](/tutorial/01-domain-model) 的顺序，7 步完成一个完整的分销系统。

### 如果你有特定场景需求

浏览 [第四部分：实战案例](/case-studies/mto-workflow-engine/)，找到与你场景类似的案例。

## 内容来源

本书的架构模式来自两个真实生产系统：

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

## 开源许可

本书内容基于 [Apache License 2.0](https://github.com/StevenTsai/distribution-architecture/blob/main/LICENSE) 开源。

欢迎提交 Issue 和 Pull Request 改进内容。

## 联系作者

- GitHub: [@StevenTsai](https://github.com/StevenTsai)
- 项目仓库: [distribution-architecture](https://github.com/StevenTsai/distribution-architecture)
