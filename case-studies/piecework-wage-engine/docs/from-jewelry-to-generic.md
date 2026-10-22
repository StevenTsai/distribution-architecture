# 从珠宝到通用：计件工资引擎

本文说明在将计件工资计算从珠宝 ERP 抽取为通用引擎时所做的设计决策。

## 原始系统

珠宝 ERP 的工资计算位于 `BizPieceworkWageServiceImpl`（142 行）。它非常直接：

```java
// 原始公式（实际代码）
int totalQty = reports.stream().mapToInt(BizProcessReport::getReportQty).sum();
BigDecimal wageAmount = process.getPiecePrice().multiply(BigDecimal.valueOf(totalQty));
```

结算流程是一系列线性方法调用：

1. `calculateWage()` —— 计算工资
2. `adjustWage()` —— 手动调整
3. `confirmWage()` —— 状态变更
4. `getMonthlySummary()` —— SQL 聚合

## 变更内容

### 1. 公式：简单相乘 → 多系数相乘

**原始：** `wage = piecePrice × totalQty`

**通用：** `wage = unitPrice × quantity × difficultyCoeff × qualityCoeff`

**原因：** 原始公式对所有工序一视同仁。而在现实中：

- 镶石比抛光更难（难度系数）
- 质量不佳的产出应被扣减（质量系数）
- 不同行业有不同的系数需求

**新增内容：**

- `WageConfig.difficultyCoefficient` —— 工序难度系数
- `WageConfig.qualityThreshold` —— 质量合格率阈值
- `WageConfig.qualityPenaltyFactor` —— 低于阈值时的扣减系数

### 2. 结算：线性调用 → 流水线（Pipeline）

**原始：** 在 `BizPieceworkWageServiceImpl` 中的顺序方法调用

**通用：** 由可插拔的 `SettlementStage` 实例组成的 `WageSettlementPipeline`

**原因：** 原始实现难以方便地新增处理步骤。若想增加税额计算，必须修改服务类。流水线（Pipeline）模式使其具备可扩展性。

### 3. 工人：“师傅” → 通用工人

**原始：** `masterId`、`masterName`（珠宝工匠）

**通用：** `workerId`、`workerName`（任意工人/员工）

**原因：** “师傅”是珠宝行业术语。通用术语适用于任何行业。

### 4. 物料损耗：移除

**原始：** `BizProcessReport` 上的 `goldLoss`、`stoneLoss`

**通用：** 不包含

**原因：** 物料损耗跟踪属于独立领域（库存/物料管理），而不属于工资计算。原始实现把两个关注点混在了一起。

### 5. 精度：隐式 → 显式

**原始：** 未指定精度（scale）或舍入模式的 `BigDecimal` 运算

**通用：** 对所有金额值使用 `setScale(2, RoundingMode.HALF_UP)`

**原因：** 原始实现依赖数据库列精度（`DECIMAL(10,2)`）来处理舍入。在代码中显式舍入更可预测、更易测试。

## 保留内容

1. **WageStatus 生命周期** —— PENDING → CONFIRMED → PAID（与原始实现相同）
2. **调整项机制** —— `finalAmount = wageAmount + adjustmentAmount`
3. **按月结算周期** —— `yyyy-MM` 格式
4. **工人汇总聚合** —— 按工人分组，对最终金额求和
5. **所有金额值均使用 BigDecimal** —— 不使用浮点数

## 新增内容

1. **`WageConfig`** —— 按工序可配置的工资参数（原始实现中不存在）
2. **`WageSettlementPipeline`** —— 可扩展的流水线模式（原始实现为线性）
3. **`SettlementStage` 接口** —— 可插拔的处理阶段
4. **`CalculateStage`** —— 显式计算阶段（原始实现内联在服务类中）
5. **带 `AdjustmentProvider` 的 `AdjustStage`** —— 自动调整能力（原始实现仅支持手动）
6. **带 `AutoConfirmCriteria` 的 `ConfirmStage`** —— 自动确认（原始实现始终为手动）
7. **`SummaryStage`** —— 内存内汇总收集（原始实现为 SQL 查询）

## 流水线扩展示例

### 标准制造业
```java
pipeline.addStage(new CalculateStage())
        .addStage(new AdjustStage())
        .addStage(new ConfirmStage())
        .addStage(new SummaryStage());
```

### 带税额计算
```java
pipeline.addStage(new CalculateStage())
        .addStage(new AdjustStage())
        .addStage(new TaxDeductionStage())  // 自定义阶段
        .addStage(new ConfirmStage())
        .addStage(new SummaryStage());
```

### 带合规检查
```java
pipeline.addStage(new CalculateStage())
        .addStage(new AdjustStage())
        .addStage(new ComplianceCheckStage())  // 自定义阶段
        .addStage(new ConfirmStage())
        .addStage(new SummaryStage());
```

## 经验总结

1. **简单公式在 v1 阶段就够用：** 珠宝系统的 `piecePrice × quantity` 公式用了多年。不要过早过度设计。

2. **系数会带来复杂度：** 难度系数和质量系数需要配置管理、阈值决策和边界情况处理。只有存在真实业务需求时才应引入。

3. **在可扩展性上流水线优于线性调用：** 如果预计会新增处理步骤，就应从流水线开始。事后改造比一开始就做对比更难。

4. **分离关注点：** 物料损耗跟踪（goldLoss/stoneLoss）不属于工资计算。抽取过程中保持领域边界清晰。

5. **显式精度：** 在 `BigDecimal` 运算中始终指定 `setScale()` 和 `RoundingMode`。不要依赖数据库列来处理舍入。
