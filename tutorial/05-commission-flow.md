# Step 05: 佣金流转 — 事件驱动的资金计算

> 前面 4 步搭建了基础设施。本章实现核心业务：业务单事件推进 → 自动生成佣金流水。

---

## 本章目标

- 创建业务单事件表（已有）和佣金流水表
- 实现事件驱动的状态机：业务单状态通过事件推进
- 实现佣金自动计算：事件发生时匹配规则、生成佣金流水
- 实现佣金冲回：业务单取消时自动冲回已生成的佣金

## 数据流全景

```
业务单事件（如"签约"）
    │
    ├── 1. 更新业务单状态
    ├── 2. 更新线索阶段
    ├── 3. 自动生成佣金流水
    ├── 4. 创建线索跟进记录
    └── 5. 记录审计日志
```

一个事件触发 5 个副作用，全部在同一个事务中。

## 第一步：状态机

业务单的状态转换硬编码在 `resolveAfterStatus()` 中：

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
    // ... 更多状态转换
    throw new BizException("当前状态不支持该事件", ...);
}
```

**为什么硬编码？** 状态转换规则是核心业务逻辑，变更频率低，硬编码可以在编译期发现错误。

## 第二步：创建事件

```java
// (source file)

@Override
@Transactional(rollbackFor = Exception.class)
public Long createEvent(Long businessOrderId, CreateDistributionBusinessOrderEventRequestDTO request) {
    DistributionBusinessOrderEntity existing = getAuthorizedBusinessOrderEntity(businessOrderId);

    // 1. 校验事件合法性
    validateEventType(eventType);
    ensureAttributionLockedForPaymentEvents(existing, eventType);
    validateEventAmount(eventType, request.getEventAmount());

    // 2. 计算目标状态
    String afterStatus = resolveAfterStatus(existing.getStatus(), eventType);

    // 3. 更新业务单（状态 + 时间戳 + 金额）
    DistributionBusinessOrderEntity update = new DistributionBusinessOrderEntity();
    update.setId(businessOrderId);
    update.setStatus(afterStatus);
    applyEventMutation(update, existing, eventType, request);
    businessOrderMapper.updateByPrimaryKeySelective(update);

    // 4. 创建事件记录
    DistributionBusinessOrderEventEntity event = new DistributionBusinessOrderEventEntity();
    event.setBizOrderId(businessOrderId);
    event.setEventNo(generateEventNo());
    event.setEventType(eventType);
    event.setEventAmount(request.getEventAmount());
    event.setEventTime(request.getEventTime());
    event.setBeforeStatus(existing.getStatus());
    event.setAfterStatus(afterStatus);
    eventMapper.insertSelective(event);

    // 5. 触发佣金计算
    DistributionBusinessOrderEntity currentOrder = getBusinessOrderEntity(businessOrderId);
    commissionService.generateForBusinessOrderEvent(currentOrder, event);

    // 6. 联动更新线索阶段
    updateLeadStageWhenBusinessOrderStatusChanged(currentOrder, afterStatus, operatorUserId);

    // 7. 创建跟进记录
    createLeadFollowUpForBusinessOrderEvent(currentOrder, event, operatorUserId);

    // 8. 审计日志
    auditLogService.record("business_order", businessOrderId, "event_" + eventType,
        beforeSnapshot, getBusinessOrder(businessOrderId), "推进业务单事件");

    return event.getId();
}
```

## 第三步：佣金自动计算

当事件发生时，自动匹配佣金规则并生成流水：

```java
// (source file)

@Override
@Transactional(rollbackFor = Exception.class)
public void generateForBusinessOrderEvent(DistributionBusinessOrderEntity businessOrder,
                                          DistributionBusinessOrderEventEntity event) {
    // 冲回场景
    if ("cancelled".equals(event.getEventType()) || "closed".equals(event.getEventType())) {
        reverseForBusinessOrderEvent(businessOrder, event);
        return;
    }

    // 幂等检查：同一事件不重复生成
    if (commissionLedgerMapper.selectByBizEventNo(event.getEventNo()) != null) return;

    // 映射触发节点
    String triggerNode = resolveTriggerNode(event.getEventType());
    // signed → "signed", first_payment → "first_payment", full_payment → "full_payment"

    // 匹配佣金规则
    ResolvedCommission resolved = resolveCommission(businessOrder, event, triggerNode);
    if (resolved == null) return;  // 无匹配规则，不生成佣金

    // 生成佣金流水
    DistributionCommissionLedgerEntity entity = new DistributionCommissionLedgerEntity();
    entity.setBizOrderId(businessOrder.getId());
    entity.setDistributorId(businessOrder.getDistributorId());
    entity.setCommissionAmount(resolved.commissionAmount);
    entity.setCommissionRuleSnapshot(writeRuleSnapshot(resolved.policy, resolved.rule));
    entity.setStatus("pending_review");
    entity.setBizEventNo(event.getEventNo());
    commissionLedgerMapper.insertSelective(entity);

    // 审计日志
    auditLogService.record("commission", entity.getId(), "create", null, entity, "自动生成佣金流水");
}
```

## 第四步：规则匹配

```java
private ResolvedCommission resolveCommission(DistributionBusinessOrderEntity order,
                                             DistributionBusinessOrderEventEntity event,
                                             String triggerNode) {
    // 1. 查找该产品当前生效的所有政策
    List<DistributionProductPolicyEntity> policies =
        policyMapper.selectActiveByProductId(order.getProductId(), event.getEventTime());

    for (DistributionProductPolicyEntity policy : policies) {
        // 2. 查找政策下的规则
        List<DistributionProductPolicyRuleEntity> rules = ruleMapper.selectByPolicyId(policy.getId());

        // 3. 按触发节点 + 渠道等级筛选
        DistributionProductPolicyRuleEntity rule = pickRule(rules, triggerNode, distributor.getLevelCode());

        // 4. 计算佣金金额
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
        BigDecimal baseAmount = event.getEventAmount();  // 以事件金额为基础
        return baseAmount.multiply(rule.getCommissionRate())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
    return null;
}
```

**关键设计**：佣金规则快照。生成佣金时，将当时的规则信息以 JSON 保存在流水记录中。这样即使后来政策变了，已生成的佣金仍然可以追溯"是按什么规则算的"。

## 第五步：佣金冲回

业务单取消/关闭时，已生成的佣金需要冲回：

```java
private void reverseForBusinessOrderEvent(DistributionBusinessOrderEntity order,
                                          DistributionBusinessOrderEventEntity event) {
    List<DistributionCommissionLedgerEntity> ledgers =
        commissionLedgerMapper.selectByBizOrderId(order.getId());

    for (DistributionCommissionLedgerEntity ledger : ledgers) {
        if (!shouldReverseLedger(ledger)) continue;

        // 生成负数冲回流水
        DistributionCommissionLedgerEntity reverse = new DistributionCommissionLedgerEntity();
        reverse.setCommissionAmount(ledger.getCommissionAmount().negate());  // 取负数
        reverse.setReverseLedgerId(ledger.getId());
        reverse.setBizEventNo(event.getEventNo() + "-R-" + ledger.getId());

        if ("settled".equals(ledger.getStatus())) {
            // 已打款：生成负数挂账，下期抵扣
            reverse.setStatus("settleable");
            markPaidSettlementReversed(ledger, reverse);
        } else {
            // 未打款：直接标记冲回
            reverse.setStatus("reversed");
            updateLedgerStatus(ledger.getId(), "reversed");
        }

        commissionLedgerMapper.insertSelective(reverse);
        auditLogService.record("commission", reverse.getId(), "create_reverse", null, reverse, "冲回佣金");
    }
}
```

**为什么冲回而非删除？** 已结算的佣金不能直接删除，需要生成负数流水在下期抵扣。这是财务系统"红字冲销"的惯例。

## 佣金流水状态机

```
PENDING_REVIEW  ──►  LOCKED  ──►  SETTLEABLE  ──►  SETTLED
      │                                            │
      └──► REVERSED                                └──► (负数挂账)
```

## 验证

```sql
-- 1. 创建业务单
-- 2. 推进事件（如签约）
-- 3. 检查佣金流水是否自动生成
SELECT * FROM distribution_commission_ledger WHERE biz_order_id = 1;

-- 4. 检查佣金规则快照
SELECT commission_rule_snapshot FROM distribution_commission_ledger WHERE id = 1;
```

## 下一步

佣金流水有了，但还没有"打款"的流程。下一步实现结算流程。

→ [Step 06: 结算流程](06-settlement.md)

---

