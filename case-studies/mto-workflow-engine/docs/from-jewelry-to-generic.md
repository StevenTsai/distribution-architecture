# 从珠宝到通用：抽象之旅

本文档说明在将 MTO 工单引擎从生产级珠宝 ERP 系统中抽取为通用、可复用组件时所做的设计决策。

## 原始系统概览

珠宝 ERP 系统（`jewelry`）是一套面向珠宝加工企业的完整生产管理系统。其生产模块通过 17 道制造工序管理工作单：

1. 模具制作
2. 注蜡
3. 种蜡树
4. 灌石膏
5. 烘烤
6. 铸造
7. 打磨
8. 执模
9. 镶石
10. 打字印
11. 抛光
12. 电镀
13. 品检
14. 称重
15. 入库
16. 配石
17. 补石

每张工单执行这些工序的一个子集，通过存储在数据库中的 `BaseProcessTemplate` 进行配置。

## 变化内容与原因

### 1. 工艺路线：数据库 → YAML

**原始**：`BaseProcessTemplate.processIds` 将工序 ID 存储为逗号分隔的字符串（例如 "1,3,5,7,9"）。这需要查询数据库才能加载，且难以进行版本控制。

**通用**：路线定义在 `workflow/` 目录下的 YAML 文件中。优势：
- 与源代码一起进行版本控制
- 无需访问数据库即可阅读
- 每个文件支持多条命名路线
- 易于创建行业特定变体

```java
// 原始：数据库查询
BaseProcessTemplate template = templateService.getById(templateId);
String[] processIdStrs = template.getProcessIds().split(",");

// 通用：YAML 配置
List<WorkOrderProcess> processes = processRouteConfig.buildProcesses("standard-furniture");
```

### 2. 物料类型：固定枚举 → 可配置分类

**原始**：`MaterialType` 枚举有 4 个固定值：`GOLD`、`STONE`、`AUXILIARY`、`FINISHED`。`MaterialCheckService` 对每种类型都有硬编码逻辑，分发到不同的库存表。

**通用**：`MaterialRequirement.materialCategory` 是自由格式的字符串。`InventoryQueryService` 接口将检查与具体实现解耦。

```java
// 原始：硬编码分发
if (MaterialType.GOLD.name().equals(materialType)) {
    // 查询黄金库存表
} else if (MaterialType.STONE.name().equals(materialType)) {
    // 查询宝石库存表
}

// 通用：基于接口
Quantity available = inventoryQueryService.getAvailableStock(
    material.getMaterialCategory(), material.getMaterialName());
```

### 3. 数量：分离字段 → 值对象

**原始**：物料数量通过多个独立的 `BigDecimal` 字段（`requiredWeight`、`issuedWeight`、`requiredQty`、`issuedQty`）跟踪，单位信息是隐式的。

**通用**：`Quantity(amount, unit)` 是一个携带单位的不可变值对象。相关操作会强制保持单位一致。

```java
// 原始：隐式单位
BigDecimal remaining = material.getRequiredWeight()
    .subtract(material.getIssuedWeight() != null ? material.getIssuedWeight() : BigDecimal.ZERO);

// 通用：显式单位
Quantity remaining = material.getRemainingNeed(); // 需求 - 已发 + 已退
```

### 4. 工人：单一字段 → 丰富模型

**原始**：工人在 `BizWorkOrder` 上由两个独立字段表示：
- `assignMaster`（Long）—— 工人 ID
- 在 `BizWoProcess` 上：
- `assignMasterId`（Long）+ `masterName`（String）

**通用**：`Worker(workerId, workerName, skillType)` 是一个带有技能类型（用于派工路由）的正式模型。

### 5. 工作中心：两个字段 → 单一模型

**原始**：工作中心是两个独立字段：
- `workshop`（String）—— 例如 "镶嵌车间"
- `team`（String）—— 例如 "A组"

**通用**：`WorkCenter(workCenterId, workCenterName, location)` 将二者合并为一个模型。

### 6. 产品：固定珠宝字段 → 灵活规格 Map

**原始**：产品信息是硬编码的：
- `material`（String）—— 例如 "18K金"
- `purity`（BigDecimal）—— 例如 0.750
- `color`（String）—— 例如 "玫瑰金"
- `styleNo`（String）—— 例如 "JS-2024-001"

**通用**：`productCode` + `productSpec`（Map）：
```java
wo.setProductCode("TABLE-OAK-001");
wo.setProductSpec(Map.of(
    "material", "Oak Wood",
    "finish", "Natural Oil",
    "dimensions", "1200x800x750mm"
));
```

### 7. 副作用：直接调用 → 事件发布

**原始**：`finishWork()` 直接创建 `BizFinishedInventory` 记录：
```java
BizFinishedInventory finished = new BizFinishedInventory();
finished.setWoId(woId);
finished.setOrderId(wo.getSalesOrderId());
finishedInventoryService.createInventory(finished);
```

**通用**：状态转换保持纯净。副作用由事件监听器处理（沿用 `distribution-architecture/architecture/commission-pipeline.md` 中的 Spring Events 模式）。

## 保留的内容

并非所有内容都需要抽象。以下模式保持原样，因为它们本身就是通用的：

1. **带守卫检查的状态机转换** —— 守卫模式适用于任何行业
2. **工序完成自动检测** —— 检查所有同级工序具有普遍适用性
3. **CompletableFuture 并行加载** —— 一种性能模式，与领域无关
4. **MyBatis 批量操作** —— 一种工程模式，与领域无关
5. **序列号生成** —— `WO` 前缀 + 日期 + 序列号在任何地方都适用
6. **软删除与审计字段** —— 标准的企业级模式

## 经验教训

1. **不要过早抽象**：珠宝系统最初使用具体命名构建。抽象是在出现真实需求（开源）时才进行的。过早抽象会拖慢开发进度。

2. **可扩展性上接口优于枚举**：固定枚举（如 `MaterialType`）起步容易，但难以扩展。接口（`InventoryQueryService`）允许在不修改核心逻辑的情况下接入新实现。

3. **配置用 YAML 而非数据库**：存放在代码中（YAML）的配置比数据库存储的配置更容易进行版本控制、评审和测试。

4. **用值对象表达领域概念**：`Quantity(amount, unit)` 比传递带有隐式单位信息的原始 `BigDecimal` 值更具表达力，也更安全。
