# From Jewelry to Generic: Spring Events Decoupling

This document explains how the three cross-module interaction patterns were extracted and refactored from the jewelry ERP into a generic Spring Events case study.

## Original System Coupling

The jewelry ERP has 6 modules that need to communicate:

```
jewelry-sales ──────────┐
jewelry-production ─────┼──→ jewelry-common (shared entities, enums)
jewelry-quality ────────┤
jewelry-finance ────────┤
jewelry-inventory ──────┘
```

Cross-module interactions found:

| From | To | Interaction | Pattern |
|------|----|-------------|---------|
| sales | finance | Delivery shipped → Create receivable | ✅ Spring Event (only one!) |
| production | inventory | Work order finished → Create finished goods | ❌ Direct service call |
| quality | production | Quality defect → Create rework order | ❌ No cross-module action |

## What Changed

### Pattern 1: Delivery → Receivable (Extracted as-is)

**Original source:** `jewelry-finance/.../DeliveryEventListener.java`

The only real Spring Event in the jewelry system. Extracted and generalized:

| Jewelry (Original) | Generic (This Case Study) |
|--------------------|--------------------------|
| `BizReceivable` entity | Callback interface parameters |
| `BizFinanceService.createReceivable()` | `AccountsReceivableCallback.createReceivable()` |
| `@Async("asyncExecutor")` | `@Async("eventAsyncExecutor")` |
| `DeliveryShippedEvent` | `DeliveryCompleteEvent` |

**Key decision:** The original listener used `@Async` for non-blocking processing. This is critical — the finance module should not block the sales module's delivery confirmation. Preserved as-is.

### Pattern 2: Work Order → Inventory (Refactored from direct call)

**Original source:** `jewelry-production/.../BizWorkOrderServiceImpl.finishWork()`

The original code directly called `finishedInventoryService.createInventory()`:

```java
// ORIGINAL: production module directly depends on inventory module
public void finishWork(Long woId) {
    wo.setStatus("ALL_FINISHED");
    updateById(wo);

    // Direct cross-module dependency!
    BizFinishedInventory finished = new BizFinishedInventory();
    finished.setWoId(woId);
    finished.setOrderId(wo.getSalesOrderId());
    finishedInventoryService.createInventory(finished);
}
```

**Problem:** The production module has a compile-time dependency on the inventory module. If the inventory service changes its API, production breaks. If you want to add another action on completion (e.g., notify sales, update analytics), you must modify the production module.

**Refactored:** Production publishes `WorkOrderFinishedEvent`, inventory listens independently.

### Pattern 3: Quality Defect → Rework (Refactored from no cross-module action)

**Original source:** `jewelry-quality/.../BizQualityServiceImpl.handleDefect()`

The original code only inserted a defect record:

```java
// ORIGINAL: no cross-module side effect
public void handleDefect(BizDefectHandle defect) {
    BizQualityDefect entity = new BizDefectHandle();
    // ... set fields
    defectHandleMapper.insert(entity);
    // Done — no event, no notification to production
}
```

**Problem:** When a defect is found, someone must manually create a rework work order. The quality module has no way to automatically notify production.

**Refactored:** Quality publishes `QualityDefectEvent`, production listens and auto-creates rework orders.

## What Was Preserved

1. **`@EventListener` annotation** — Spring's native event mechanism, no additional library needed
2. **`@Async` on listeners** — Non-blocking, fire-and-forget pattern
3. **try/catch in listeners** — Failures logged, never propagated (prevents upstream rollback)
4. **Thread pool configuration** — Core 2, max 8, queue 100, CallerRunsPolicy (from original `AsyncConfig`)
5. **`ApplicationEvent` base class** — Carries event data, supports event hierarchy

## What Was Added

1. **Callback interfaces** — The original listeners directly injected service beans. This version uses callback interfaces (`AccountsReceivableCallback`, `FinishedGoodsCallback`, `ReworkOrderCallback`) for:
   - Unit testing without Spring context
   - Loose coupling (listener doesn't know the concrete service)
   - Flexibility (different implementations for different environments)

2. **`QualityDefectEvent`** — Didn't exist in the original. Added to demonstrate the pattern of refactoring a direct call into an event.

3. **`WorkOrderFinishedEvent`** — Didn't exist in the original. Added to demonstrate the pattern of refactoring a direct call into an event.

4. **Configurable payment terms** — The original hardcoded 30-day terms. This version accepts it as a constructor parameter.

## Transaction Considerations

The original `DeliveryShippedEvent` was published inside a `@Transactional` method. This means:

- **Default behavior:** The listener fires synchronously before the transaction commits
- **With `@Async`:** The listener runs in a separate thread, may execute before commit
- **With `@TransactionalEventListener`:** Can defer until after commit (not used here, but available)

This is a known trade-off. The original jewelry system accepted eventual consistency (fire-and-forget) for these side effects, which is correct for:
- Accounts receivable creation (can be retried)
- Finished goods inventory (can be reconciled)
- Rework order creation (can be manually triggered)

## Lessons Learned

1. **Start with the real code:** Only 1 of 3 patterns actually used events. The other 2 were direct calls. The case study value is showing how to migrate from direct calls to events.

2. **Events don't replace transactions:** Events are for cross-module communication, not for within-module business logic. The delivery status update stays in the same transaction; only the receivable creation is event-driven.

3. **Fire-and-forget is often correct:** In a distributed system, trying to make everything synchronous and transactional leads to tight coupling and poor resilience. The try/catch + log pattern is pragmatic and production-proven.

4. **Callback interfaces > concrete services:** The original listener injected `BizFinanceService` directly. The callback interface makes testing trivial and keeps the case study focused on the event pattern, not the service implementation.
