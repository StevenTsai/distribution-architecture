# Step 05: Commission Flow — Event-Driven Fund Calculation

[中文](../../tutorial/05-commission-flow.md) | English

> The previous four steps built the infrastructure. This chapter implements the core business logic: business order event progression → automatic generation of commission ledger entries.

---

## Chapter Goals

- Create the business order event table (already exists) and the commission ledger table
- Implement an event-driven state machine: business order status is advanced through events
- Implement automatic commission calculation: match rules and generate commission ledger entries when events occur
- Implement commission reversal: automatically reverse generated commissions when a business order is cancelled

## Data Flow Overview

```
Business order event (e.g., "signed")
    │
    ├── 1. Update business order status
    ├── 2. Update lead stage
    ├── 3. Automatically generate commission ledger entries
    ├── 4. Create a lead follow-up record
    └── 5. Record an audit log
```

One event triggers five side effects, all within the same transaction.

## Step 1: State Machine

Business order status transitions are hardcoded in `resolveAfterStatus()`:

```java
private String resolveAfterStatus(String currentStatus, String eventType) {
    if ("draft".equals(currentStatus)) {
        if ("visited".equals(eventType)) return "visiting";
        if ("signed".equals(eventType)) return "signed";
        if ("cancelled".equals(eventType)) return "cancelled";
    }
    if ("visiting".equals(currentStatus)) {
        if ("signed".equals(eventType)) return "signed";
        if ("cancelled".equals(eventType)) return "cancelled";
    }
    if ("signed".equals(currentStatus)) {
        if ("first_payment".equals(eventType)) return "first_paid";
        if ("full_payment".equals(eventType)) return "full_paid";
        if ("cancelled".equals(eventType)) return "cancelled";
    }
    // ... more state transitions
    throw new BizException("The current status does not support this event", ...);
}
```

**Why hardcode?** State transition rules are core business logic with a low change frequency, and hardcoding allows errors to be caught at compile time.

## Step 2: Create an Event

```java
// (source file)

@Override
@Transactional(rollbackFor = Exception.class)
public Long createEvent(Long businessOrderId, CreateDistributionBusinessOrderEventRequestDTO request) {
    DistributionBusinessOrderEntity existing = getAuthorizedBusinessOrderEntity(businessOrderId);

    // 1. Validate event validity
    validateEventType(eventType);
    ensureAttributionLockedForPaymentEvents(existing, eventType);
    validateEventAmount(eventType, request.getEventAmount());

    // 2. Calculate the target status
    String afterStatus = resolveAfterStatus(existing.getStatus(), eventType);

    // 3. Update the business order (status + timestamp + amount)
    DistributionBusinessOrderEntity update = new DistributionBusinessOrderEntity();
    update.setId(businessOrderId);
    update.setStatus(afterStatus);
    applyEventMutation(update, existing, eventType, request);
    businessOrderMapper.updateByPrimaryKeySelective(update);

    // 4. Create the event record
    DistributionBusinessOrderEventEntity event = new DistributionBusinessOrderEventEntity();
    event.setBizOrderId(businessOrderId);
    event.setEventNo(generateEventNo());
    event.setEventType(eventType);
    event.setEventAmount(request.getEventAmount());
    event.setEventTime(request.getEventTime());
    event.setBeforeStatus(existing.getStatus());
    event.setAfterStatus(afterStatus);
    eventMapper.insertSelective(event);

    // 5. Trigger commission calculation
    DistributionBusinessOrderEntity currentOrder = getBusinessOrderEntity(businessOrderId);
    commissionService.generateForBusinessOrderEvent(currentOrder, event);

    // 6. Synchronize the lead stage update
    updateLeadStageWhenBusinessOrderStatusChanged(currentOrder, afterStatus, operatorUserId);

    // 7. Create a follow-up record
    createLeadFollowUpForBusinessOrderEvent(currentOrder, event, operatorUserId);

    // 8. Audit log
    auditLogService.record("business_order", businessOrderId, "event_" + eventType,
        beforeSnapshot, getBusinessOrder(businessOrderId), "Advance business order event");

    return event.getId();
}
```

## Step 3: Automatic Commission Calculation

When an event occurs, automatically match commission rules and generate ledger entries:

```java
// (source file)

@Override
@Transactional(rollbackFor = Exception.class)
public void generateForBusinessOrderEvent(DistributionBusinessOrderEntity businessOrder,
                                          DistributionBusinessOrderEventEntity event) {
    // Reversal scenario
    if ("cancelled".equals(event.getEventType()) || "closed".equals(event.getEventType())) {
        reverseForBusinessOrderEvent(businessOrder, event);
        return;
    }

    // Idempotency check: do not generate duplicates for the same event
    if (commissionLedgerMapper.selectByBizEventNo(event.getEventNo()) != null) return;

    // Map the trigger node
    String triggerNode = resolveTriggerNode(event.getEventType());
    // signed → "signed", first_payment → "first_payment", full_payment → "full_payment"

    // Match the commission rule
    ResolvedCommission resolved = resolveCommission(businessOrder, event, triggerNode);
    if (resolved == null) return;  // No matching rule, do not generate commission

    // Generate the commission ledger entry
    DistributionCommissionLedgerEntity entity = new DistributionCommissionLedgerEntity();
    entity.setBizOrderId(businessOrder.getId());
    entity.setDistributorId(businessOrder.getDistributorId());
    entity.setCommissionAmount(resolved.commissionAmount);
    entity.setCommissionRuleSnapshot(writeRuleSnapshot(resolved.policy, resolved.rule));
    entity.setStatus("pending_review");
    entity.setBizEventNo(event.getEventNo());
    commissionLedgerMapper.insertSelective(entity);

    // Audit log
    auditLogService.record("commission", entity.getId(), "create", null, entity, "Automatically generate commission ledger entry");
}
```

## Step 4: Rule Matching

```java
private ResolvedCommission resolveCommission(DistributionBusinessOrderEntity order,
                                             DistributionBusinessOrderEventEntity event,
                                             String triggerNode) {
    // 1. Find all currently active policies for this product
    List<DistributionProductPolicyEntity> policies =
        policyMapper.selectActiveByProductId(order.getProductId(), event.getEventTime());

    for (DistributionProductPolicyEntity policy : policies) {
        // 2. Find the rules under the policy
        List<DistributionProductPolicyRuleEntity> rules = ruleMapper.selectByPolicyId(policy.getId());

        // 3. Filter by trigger node + distributor level
        DistributionProductPolicyRuleEntity rule = pickRule(rules, triggerNode, distributor.getLevelCode());

        // 4. Calculate the commission amount
        BigDecimal amount = calculateCommissionAmount(rule, order, event);
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            return new ResolvedCommission(policy, rule, amount);
        }
    }
    return null;
}

private BigDecimal calculateCommissionAmount(DistributionProductPolicyRuleEntity rule,
                                             DistributionBusinessOrderEntity order,
                                             DistributionBusinessOrderEventEntity event) {
    if ("fixed".equals(rule.getRuleType())) {
        return rule.getFixedAmount();
    }
    if ("rate".equals(rule.getRuleType())) {
        BigDecimal baseAmount = event.getEventAmount();  // Use the event amount as the base
        return baseAmount.multiply(rule.getCommissionRate())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
    return null;
}
```

**Key design**: the commission rule snapshot. When a commission is generated, the rule information at that time is saved as JSON in the ledger entry. This way, even if the policy changes later, a generated commission can still be traced back to "which rules it was calculated under."

## Step 5: Commission Reversal

When a business order is cancelled/closed, generated commissions must be reversed:

```java
private void reverseForBusinessOrderEvent(DistributionBusinessOrderEntity order,
                                          DistributionBusinessOrderEventEntity event) {
    List<DistributionCommissionLedgerEntity> ledgers =
        commissionLedgerMapper.selectByBizOrderId(order.getId());

    for (DistributionCommissionLedgerEntity ledger : ledgers) {
        if (!shouldReverseLedger(ledger)) continue;

        // Generate a negative reversal entry
        DistributionCommissionLedgerEntity reverse = new DistributionCommissionLedgerEntity();
        reverse.setCommissionAmount(ledger.getCommissionAmount().negate());  // Negate
        reverse.setReverseLedgerId(ledger.getId());
        reverse.setBizEventNo(event.getEventNo() + "-R-" + ledger.getId());

        if ("settled".equals(ledger.getStatus())) {
            // Already paid: generate a negative offset entry, to be deducted in the next period
            reverse.setStatus("settleable");
            markPaidSettlementReversed(ledger, reverse);
        } else {
            // Not paid: mark as reversed directly
            reverse.setStatus("reversed");
            updateLedgerStatus(ledger.getId(), "reversed");
        }

        commissionLedgerMapper.insertSelective(reverse);
        auditLogService.record("commission", reverse.getId(), "create_reverse", null, reverse, "Reverse commission");
    }
}
```

**Why reverse rather than delete?** A settled commission cannot be deleted directly; a negative ledger entry must be generated to offset it in the next period. This follows the accounting convention of "red-letter reversal."

## Commission Ledger State Machine

```
PENDING_REVIEW  ──►  LOCKED  ──►  SETTLEABLE  ──►  SETTLED
      │                                            │
      └──► REVERSED                                └──► (negative offset entry)
```

## Verification

```sql
-- 1. Create a business order
-- 2. Advance an event (e.g., signed)
-- 3. Check whether a commission ledger entry was generated automatically
SELECT * FROM distribution_commission_ledger WHERE biz_order_id = 1;

-- 4. Check the commission rule snapshot
SELECT commission_rule_snapshot FROM distribution_commission_ledger WHERE id = 1;
```

## Next Steps

Commission ledger entries now exist, but there is still no payout process. The next step implements the settlement flow.

→ [Step 06: Settlement Flow](06-settlement.md)

---
