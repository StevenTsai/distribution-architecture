# 安全政策

## 支持的版本

由于这是一个文档和示例代码仓库，我们只对最新版本（main 分支）提供安全更新。

| 版本 | 支持状态 |
|------|---------|
| main | ✅ 最新 |
| feature 分支 | ⚠️ 开发中 |

## 报告漏洞

**请不要通过公开的 GitHub Issues 报告安全漏洞。**

请通过以下方式报告安全问题：

1. **GitHub Security Advisories**（推荐）：
   https://github.com/StevenTsai/distribution-architecture/security/advisories/new

2. **邮件联系**：通过 GitHub 个人资料中的联系方式

## 我们的承诺

- 我们会在 **48 小时内** 确认收到您的报告
- 我们会在 **7 天内** 提供初步评估
- 我们会在修复后 **公开致谢**（除非您要求匿名）

## 安全相关文档

本仓库中的以下文档涉及安全主题：

- [PII 加密与脱敏](architecture/pii-encryption.md) — 个人数据保护
- [管理端 RBAC](architecture/admin-rbac.md) — 访问控制
- [数据权限模型](architecture/data-permission-model.md) — 行级数据隔离
- [审计日志与合规](architecture/audit-and-compliance.md) — 操作追溯

## 免责声明

本仓库中的代码示例仅用于学习目的。在生产环境中使用前，请进行充分的安全评估和测试。
