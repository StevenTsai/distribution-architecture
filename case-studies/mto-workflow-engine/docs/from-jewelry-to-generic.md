# From Jewelry to Generic: The Abstraction Journey

This document explains the design decisions made when extracting the MTO workflow engine from a production jewelry ERP system into a generic, reusable component.

## Original System Overview

The jewelry ERP system (`jewelry`) is a complete production management system for jewelry processing businesses. Its production module manages work orders through a 17-step manufacturing process:

1. 模具制作 (Mold Making)
2. 注蜡 (Wax Injection)
3. 种蜡树 (Wax Tree)
4. 灌石膏 (Plaster Pouring)
5. 烘烤 (Baking)
6. 铸造 (Casting)
7. 打磨 (Grinding)
8. 执模 (Modeling)
9. 镶石 (Stone Setting)
10. 打字印 (Stamping)
11. 抛光 (Polishing)
12. 电镀 (Plating)
13. 品检 (QC Inspection)
14. 称重 (Weighing)
15. 入库 (Warehousing)
16. 配石 (Stone Matching)
17. 补石 (Stone Repair)

Each work order follows a subset of these processes, configured via a `BaseProcessTemplate` stored in the database.

## What Changed and Why

### 1. Process Route: Database → YAML

**Original**: `BaseProcessTemplate.processIds` stored process IDs as comma-separated strings (e.g. "1,3,5,7,9"). This required database queries to load and was hard to version-control.

**Generic**: Routes are defined in YAML files under `workflow/`. Benefits:
- Version-controlled with source code
- Readable without database access
- Supports multiple named routes per file
- Easy to create industry-specific variants

```java
// Original: database lookup
BaseProcessTemplate template = templateService.getById(templateId);
String[] processIdStrs = template.getProcessIds().split(",");

// Generic: YAML config
List<WorkOrderProcess> processes = processRouteConfig.buildProcesses("standard-furniture");
```

### 2. Material Types: Fixed Enum → Configurable Categories

**Original**: `MaterialType` enum had 4 fixed values: `GOLD`, `STONE`, `AUXILIARY`, `FINISHED`. The `MaterialCheckService` had hardcoded logic for each type, dispatching to different inventory tables.

**Generic**: `MaterialRequirement.materialCategory` is a free-form string. The `InventoryQueryService` interface decouples the check from specific implementations.

```java
// Original: hardcoded dispatch
if (MaterialType.GOLD.name().equals(materialType)) {
    // query gold inventory table
} else if (MaterialType.STONE.name().equals(materialType)) {
    // query stone inventory table
}

// Generic: interface-based
Quantity available = inventoryQueryService.getAvailableStock(
    material.getMaterialCategory(), material.getMaterialName());
```

### 3. Quantity: Separate Fields → Value Object

**Original**: Material quantities were tracked via separate `BigDecimal` fields (`requiredWeight`, `issuedWeight`, `requiredQty`, `issuedQty`) with implicit unit knowledge.

**Generic**: `Quantity(amount, unit)` is an immutable value object that carries its unit. Operations enforce unit consistency.

```java
// Original: implicit units
BigDecimal remaining = material.getRequiredWeight()
    .subtract(material.getIssuedWeight() != null ? material.getIssuedWeight() : BigDecimal.ZERO);

// Generic: explicit units
Quantity remaining = material.getRemainingNeed(); // required - issued + returned
```

### 4. Worker: Single Field → Rich Model

**Original**: Workers were represented by two separate fields on `BizWorkOrder`:
- `assignMaster` (Long) — the worker ID
- And on `BizWoProcess`:
- `assignMasterId` (Long) + `masterName` (String)

**Generic**: `Worker(workerId, workerName, skillType)` is a proper model with a skill type for routing.

### 5. Work Center: Two Fields → Single Model

**Original**: Work centers were two separate fields:
- `workshop` (String) — e.g. "镶嵌车间"
- `team` (String) — e.g. "A组"

**Generic**: `WorkCenter(workCenterId, workCenterName, location)` combines both into one model.

### 6. Product: Fixed Jewelry Fields → Flexible Spec Map

**Original**: Product info was hardcoded:
- `material` (String) — e.g. "18K金"
- `purity` (BigDecimal) — e.g. 0.750
- `color` (String) — e.g. "玫瑰金"
- `styleNo` (String) — e.g. "JS-2024-001"

**Generic**: `productCode` + `productSpec` (Map):
```java
wo.setProductCode("TABLE-OAK-001");
wo.setProductSpec(Map.of(
    "material", "Oak Wood",
    "finish", "Natural Oil",
    "dimensions", "1200x800x750mm"
));
```

### 7. Side Effects: Direct Calls → Event Publishing

**Original**: `finishWork()` directly created `BizFinishedInventory` records:
```java
BizFinishedInventory finished = new BizFinishedInventory();
finished.setWoId(woId);
finished.setOrderId(wo.getSalesOrderId());
finishedInventoryService.createInventory(finished);
```

**Generic**: State transitions are pure. Side effects are handled by event listeners (following the Spring Events pattern from `distribution-architecture/architecture/commission-pipeline.md`).

## What Was Preserved

Not everything needed abstraction. These patterns were kept as-is because they're already generic:

1. **State machine with guard-checked transitions** — the guard pattern works for any industry
2. **Process completion auto-detection** — checking all siblings is universally applicable
3. **CompletableFuture parallel loading** — a performance pattern, not domain-specific
4. **MyBatis Batch operations** — an engineering pattern, not domain-specific
5. **Sequence number generation** — `WO` prefix + date + sequence works everywhere
6. **Soft delete and audit fields** — standard enterprise patterns

## Lessons Learned

1. **Don't abstract too early**: The jewelry system was built with concrete names first. Abstraction came when there was a real need (open-sourcing). Premature abstraction would have slowed development.

2. **Interfaces over enums for extensibility**: Fixed enums (like `MaterialType`) are easy to start with but hard to extend. Interfaces (`InventoryQueryService`) allow plugging in new implementations without changing core logic.

3. **YAML over database for configuration**: Configuration that lives in code (YAML) is easier to version, review, and test than database-stored configuration.

4. **Value objects for domain concepts**: `Quantity(amount, unit)` is more expressive and safer than passing around raw `BigDecimal` values with implicit unit knowledge.
