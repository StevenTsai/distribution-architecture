# 贡献指南

感谢你对 Distribution Architecture 的关注！以下是参与贡献的方式。

## 如何贡献

### 报告问题

如果你发现了文档错误、代码 bug 或有改进建议：

1. 在 GitHub Issues 中搜索是否已有类似问题
2. 如果没有，创建一个新的 Issue，包含：
   - 清晰的标题和描述
   - 复现步骤（如果是 bug）
   - 期望的行为 vs 实际的行为
   - 相关的环境信息

### 提交 Pull Request

1. Fork 本仓库
2. 创建你的特性分支：`git checkout -b feature/my-improvement`
3. 提交你的修改：`git commit -m 'Add some improvement'`
4. 推送到分支：`git push origin feature/my-improvement`
5. 创建一个 Pull Request

### PR 规范

- **标题**：简洁描述修改内容，如"Fix data permission SQL typo in tutorial 02"
- **描述**：说明修改的原因和影响范围
- **关联 Issue**：如果有相关 Issue，请在描述中引用
- **一个 PR 做一件事**：避免在一个 PR 中混合多个不相关的修改

## 贡献类型

### 文档贡献

- 修复错别字或语法错误
- 改进代码示例的可读性
- 补充缺失的说明或注释
- 翻译（英文版文档）

### 代码贡献（distribution-starter）

- 修复 bug
- 添加新的设计模式示例
- 改进测试覆盖率
- 升级依赖版本

### 设计贡献

- 提出新的架构决策记录（ADR）
- 对现有设计提出改进建议
- 分享你的二次开发经验

## 开发环境

### 文档项目

文档是纯 Markdown 文件，可以直接编辑：

```bash
git clone https://github.com/your-username/distribution-architecture.git
cd distribution-architecture
# 编辑 Markdown 文件即可
```

### Starter 项目

```bash
cd distribution-starter
# 确保已安装 Java 17+ 和 Maven 3.6+
mvn clean compile
mvn test
```

## 文档规范

- 使用中文撰写
- 代码示例使用 Java 语法高亮
- 每个设计模式文档包含：问题描述、设计决策、代码示例、trade-off 分析
- ADR 文档遵循 [ADR 模板](https://adr.github.io/)

## 行为准则

- 尊重每位贡献者
- 建设性地提出反馈
- 聚焦于技术讨论
- 欢迎新手提问

## 许可证

提交贡献即表示你同意你的贡献将在 [Apache License 2.0](LICENSE) 下发布。
