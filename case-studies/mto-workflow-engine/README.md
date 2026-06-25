# Case Study: MTO Workflow Engine

A generic Make-To-Order (MTO) workflow engine extracted from a production jewelry ERP system.

## What Is This

This case study demonstrates how to abstract a domain-specific workflow engine into a reusable, industry-agnostic component. The original implementation managed jewelry production orders with 17 fixed processes (casting, setting, polishing, plating...). This version uses configurable YAML routes and generic models, making it applicable to furniture, electronics, apparel, and other manufacturing industries.

## Background

The original jewelry ERP system (`jewelry`) has:
- **152 API endpoints** across 6 modules
- **44 database tables**
- **909 test cases**
- **Production-validated** in a real jewelry processing business

The MTO workflow engine is the core of its production module, managing the complete lifecycle of production orders.

## State Machine

```
                                    ┌──────────────────────────────────┐
                                    │                                  │
[new] ──create──→ PENDING ──schedule──→ SCHEDULED ──startWork──→ PRODUCING
  │                 │                                                │
  │                 │ markAsMaterialReady                             │ finishProcess (partial)
  │                 ↓                                                ↓
  │            MATERIAL_READY ──schedule──→ SCHEDULED      PARTIAL_FINISHED
  │                                                          │
  │                                      ┌───────────────────┘
  │                                      │ finishProcess (all done) / finishWork
  │                                      ↓
  │                                 ALL_FINISHED ──→ WAREHOUSED ──→ CLOSED
  │                                      │                            ↑
  │                                      └──────────closeWork─────────┘
  │
  └──voidWork──→ VOID (from any except CLOSED/VOID)
```

### State Transitions

| Transition | Guard Condition | Side Effect |
|-----------|----------------|-------------|
| `create()` | — | Generates WO number, builds process route |
| `schedule()` | PENDING or MATERIAL_READY | Assigns work center + worker |
| `startWork()` | SCHEDULED | Records actual start date |
| `finishProcess()` | PRODUCING or PARTIAL_FINISHED | Auto-detects if all processes done |
| `finishWork()` | PRODUCING or PARTIAL_FINISHED | Marks all processes complete |
| `closeWork()` | ALL_FINISHED or WAREHOUSED | Archives the work order |
| `voidWork()` | Not CLOSED, not VOID | Records cancellation reason |

## Key Design Patterns

### 1. Process Route via YAML Configuration

Instead of storing process IDs as comma-separated strings in the database (original approach), routes are defined in YAML:

```yaml
mto:
  routes:
    - name: "standard-furniture"
      processes:
        - code: "CUT"
          name: "Cutting"
          plannedHours: 2.0
        - code: "SAND"
          name: "Sanding"
          plannedHours: 1.5
```

### 2. Process Completion Auto-Detection

When a process finishes, the engine checks ALL sibling processes. If none are incomplete, the work order automatically advances to ALL_FINISHED. This pattern was critical in the jewelry system where 17 processes could complete in any order.

### 3. Interface-Based Inventory Query

Material readiness checks are decoupled from specific inventory implementations via `InventoryQueryService`. The original was tightly coupled to gold and stone inventory tables.

### 4. Event-Driven Side Effects

The original `finishWork()` directly created finished goods inventory records. This version only does the state transition; side effects should be handled by event listeners (see `distribution-architecture/architecture/commission-pipeline.md` for the event pattern).

## Project Structure

```
src/main/java/com/distribution/casestudy/mto/
├── model/
│   ├── WorkOrder.java              # Core work order entity
│   ├── WorkOrderProcess.java       # Process within a work order
│   ├── WorkOrderStatus.java        # 9-state lifecycle enum
│   ├── ProcessStatus.java          # 4-state process enum
│   ├── MaterialRequirement.java    # Material requirement tracking
│   ├── Quantity.java               # Value object: amount + unit
│   ├── Worker.java                 # Generic worker model
│   └── WorkCenter.java             # Production work center model
├── service/
│   ├── WorkOrderStateMachine.java  # State machine core
│   ├── MaterialCheckService.java   # Material readiness check
│   └── InventoryQueryService.java  # Inventory query interface
└── config/
    └── ProcessRouteConfig.java     # YAML route configuration
```

## Abstraction Mapping

| Jewelry (Original) | Generic (This Case Study) | Industry Examples |
|--------------------|--------------------------|--------------------|
| 17 fixed processes (casting, setting...) | YAML-configurable routes | Cutting, SMT, Sewing |
| Gold / Stone / Auxiliary | MaterialCategory + MaterialName | Wood, PCB, Fabric |
| Grams / Carats | `Quantity(amount, unit)` | kg, pcs, meters |
| Master / Craftsman | `Worker(workerId, name, skillType)` | Operator, Technician |
| Workshop + Team | `WorkCenter(id, name, location)` | Production Line, Cell |
| goldLoss / stoneLoss | `materialLoss` (generic) | Waste, Scrap |
| styleNo | `productCode` | SKU, Part Number |
| material / purity / color | `productSpec` (Map) | Configurable attributes |

## How to Use

### 1. Define a Process Route in YAML

```yaml
mto:
  routes:
    - name: "my-production-route"
      processes:
        - code: "STEP1"
          name: "First Step"
          plannedHours: 1.0
        - code: "STEP2"
          name: "Second Step"
          plannedHours: 2.0
```

### 2. Create and Manage a Work Order

```java
// Create from YAML route
WorkOrderStateMachine stateMachine = new WorkOrderStateMachine(routeConfig);
WorkOrder wo = stateMachine.create("WO-001", "my-production-route");

// Schedule
stateMachine.schedule(wo, new WorkCenter(1L, "Line A"), new Worker(1L, "Alice"));

// Start production
stateMachine.startWork(wo);

// Complete processes one by one
stateMachine.finishProcess(wo, processId);

// When all processes done → auto ALL_FINISHED
// Then close
stateMachine.closeWork(wo);
```

## Related Documents

- [Data Permission Model](../architecture/data-permission-model.md) — The permission pattern used in the original system
- [Commission Pipeline](../architecture/commission-pipeline.md) — Event-driven pattern for side effects
- [MyBatis Patterns](../architecture/mybatis-patterns.md) — SQL patterns used in the original implementation
- [Open Source Strategy](../doc/open-source-strategy.md) — The strategy behind this extraction

## Source

Extracted from the `jewelry` production ERP system (private repository). The original code has been validated in a real business environment with 152 API endpoints and 44 database tables.
