# Case Study: Spring Events Cross-Module Decoupling

Demonstrates how to decouple cross-module interactions using Spring Events, extracted from a production jewelry ERP and generalized for any industry.

## What Is This

This case study shows three event-driven patterns that replace direct synchronous service calls between modules. In the original jewelry ERP, modules were tightly coupled — the production module directly called inventory services, the quality module directly called production services. This case study refactors those into events, making each module independent.

## Background

The jewelry ERP has 6 modules (`jewelry-sales`, `jewelry-production`, `jewelry-quality`, `jewelry-finance`, `jewelry-inventory`, `jewelry-system`). Cross-module interactions are the main source of coupling.

**Original coupling patterns found:**

| Interaction | Original Pattern | This Case Study |
|------------|-----------------|-----------------|
| Delivery → Receivable | ✅ Already uses `@EventListener` | Extracted and generalized |
| Work Order → Inventory | ❌ Direct `finishedInventoryService.createInventory()` call | Refactored to event |
| Quality Defect → Rework | ❌ Direct `workOrderService.updateById()` call | Refactored to event |

## The Three Event Patterns

### 1. Delivery → Accounts Receivable

```
Sales Module                    Finance Module
    │                               │
    ├─ shipDelivery()               │
    │   ├─ update delivery status   │
    │   ├─ update order status      │
    │   │                           │
    │   └─ publishEvent ──────────→ │ @EventListener
    │      DeliveryCompleteEvent    │   createReceivable()
    │                               │   (30-day payment term)
```

**Source:** Real pattern from `jewelry-finance/DeliveryEventListener.java`

### 2. Work Order → Finished Goods Inventory

```
Production Module                Inventory Module
    │                               │
    ├─ finishWork()                 │
    │   ├─ set ALL_FINISHED         │
    │   │                           │
    │   └─ publishEvent ──────────→ │ @EventListener
    │      WorkOrderFinishedEvent   │   createFinishedGoods()
    │                               │
    │ (BEFORE: direct call to       │
    │  finishedInventoryService)    │
```

**Source:** Refactored from `jewelry-production/BizWorkOrderServiceImpl.finishWork()` which directly called `finishedInventoryService.createInventory()`.

### 3. Quality Defect → Rework Work Order

```
Quality Module                   Production Module
    │                               │
    ├─ handleDefect()               │
    │   ├─ record defect            │
    │   │                           │
    │   └─ publishEvent ──────────→ │ @EventListener
    │      QualityDefectEvent       │   createReworkOrder()
    │                               │
    │ (BEFORE: no cross-module      │
    │  side effect at all)          │
```

**Source:** Refactored from `jewelry-quality/BizQualityServiceImpl.handleDefect()` which only inserted a defect record without triggering rework.

## Key Design Patterns

### 1. Fire-and-Forget with Error Isolation

All listeners wrap their logic in try/catch. Failures are logged but never propagated to the publisher. This prevents a downstream failure from rolling back the upstream transaction.

```java
@EventListener
@Async("eventAsyncExecutor")
public void onDeliveryComplete(DeliveryCompleteEvent event) {
    try {
        // ... create receivable
    } catch (Exception e) {
        log.error("Failed: {}", e.getMessage(), e);
        // Don't propagate — fire-and-forget
    }
}
```

### 2. Callback Interface for Testability

Each listener accepts a callback interface instead of a concrete service. This makes unit testing trivial — no Spring context, no mocking framework needed.

```java
public class DeliveryToReceivableListener {
    private final AccountsReceivableCallback receivableCallback;

    public interface AccountsReceivableCallback {
        void createReceivable(...);
    }
}
```

### 3. @Async for Non-Blocking Processing

The `@Async("eventAsyncExecutor")` annotation ensures listeners run in a thread pool, not blocking the publisher's transaction. Combined with `@TransactionalEventListener` considerations for transaction-aware processing.

### 4. Dedicated Thread Pool

Extracted from the jewelry ERP's `AsyncConfig` (core 2, max 8, queue 100, CallerRunsPolicy). The CallerRunsPolicy provides backpressure — when the pool is saturated, the publisher thread handles the event itself, preventing event loss.

## Project Structure

```
src/main/java/com/distribution/casestudy/events/
├── event/
│   ├── DeliveryCompleteEvent.java      # 发货完成 → 触发应收账款
│   ├── WorkOrderFinishedEvent.java     # 工单完工 → 触发成品入库
│   └── QualityDefectEvent.java         # 质检不良 → 触发返工工单
├── listener/
│   ├── DeliveryToReceivableListener.java   # 发货 → 应收账款
│   ├── WorkOrderToInventoryListener.java   # 完工 → 成品入库
│   └── QualityToReworkListener.java        # 不良 → 返工工单
└── config/
    └── EventConfig.java              # 异步线程池 + 监听器注册
```

## How to Use

### 1. Define an Event

```java
public class DeliveryCompleteEvent extends ApplicationEvent {
    private final Long deliveryId;
    private final Long orderId;
    // ... other fields
}
```

### 2. Publish the Event

```java
@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final ApplicationEventPublisher eventPublisher;

    public void shipDelivery(Long id) {
        // ... update status
        eventPublisher.publishEvent(new DeliveryCompleteEvent(
                this, delivery.getId(), order.getId(),
                order.getOrderNo(), order.getCustomerId(), order.getAmount()));
    }
}
```

### 3. Listen for the Event

```java
@Slf4j
public class DeliveryToReceivableListener {
    @EventListener
    @Async("eventAsyncExecutor")
    public void onDeliveryComplete(DeliveryCompleteEvent event) {
        try {
            receivableCallback.createReceivable(...);
        } catch (Exception e) {
            log.error("Failed", e);
        }
    }
}
```

## Migration Guide: Direct Call → Event

### Before (tightly coupled)

```java
// Production module directly depends on Inventory module
@Service
public class WorkOrderServiceImpl {
    private final FinishedInventoryService inventoryService;

    public void finishWork(Long woId) {
        wo.setStatus("ALL_FINISHED");
        updateById(wo);
        // Direct cross-module dependency!
        inventoryService.createInventory(finished);
    }
}
```

### After (decoupled)

```java
// Production module — no dependency on Inventory module
@Service
public class WorkOrderServiceImpl {
    private final ApplicationEventPublisher eventPublisher;

    public void finishWork(Long woId) {
        wo.setStatus("ALL_FINISHED");
        updateById(wo);
        eventPublisher.publishEvent(new WorkOrderFinishedEvent(this, ...));
    }
}

// Inventory module — listens independently
@Slf4j
public class WorkOrderToInventoryListener {
    @EventListener
    @Async("eventAsyncExecutor")
    public void onWorkOrderFinished(WorkOrderFinishedEvent event) {
        finishedGoodsCallback.createFinishedGoods(...);
    }
}
```

## Related Documents

- [MTO Workflow Engine](../mto-workflow-engine/) — The work order state machine that publishes `WorkOrderFinishedEvent`
- [Commission Pipeline](../../architecture/commission-pipeline.md) — Event-driven pattern for commission calculation
- [Data Permission Model](../../architecture/data-permission-model.md) — Cross-cutting concern pattern

## Source

Extracted from the `jewelry` production ERP system. The delivery→receivable event was the only actual Spring Event implementation; the other two patterns were refactored from direct service calls to demonstrate the event-driven approach.
