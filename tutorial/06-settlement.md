# Step 06: 结算流程 — 从佣金到打款

> 佣金流水生成了，但还没有"把钱给渠道"的流程。本章实现结算单的创建、审核、打款。

---

## 本章目标

- 理解结算单与佣金流水的关系
- 实现结算单的创建（按渠道+时间段归集）
- 实现审核→打款的流程
- 理解冲回对结算单的影响

## 结算单 vs 佣金流水

```
佣金流水（多条）              结算单（一条）
┌────────────────────┐      ┌────────────────────┐
│ 流水1: +5000       │──┐   │ 结算单 #DST001     │
│ 流水2: +3000       │──┼──►│ 渠道: A 渠道       │
│ 流水3: +2000       │──┘   │ 期间: 2026-06      │
└────────────────────┘      │ 应结: 10000        │
                            │ 冲回: 0            │
                            │ 实结: 10000        │
                            └────────────────────┘
```

一条结算单对应多条佣金流水。结算单是"给渠道的付款凭证"。

## 第一步：佣金流水的回款录入

佣金流水从 `PENDING_REVIEW` 到 `SETTLEABLE`，需要录入回款信息：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void recordReceipt(Long ledgerId, RecordCommissionReceiptRequestDTO request) {
    DistributionCommissionLedgerEntity ledger = getLedgerEntity(ledgerId);

    // 校验状态
    if (!"pending_review".equals(ledger.getStatus())
        && !"locked".equals(ledger.getStatus())) {
        throw new BizException("当前状态不允许录入回款", ...);
    }

    // 校验金额
    if (request.getReceivedAmount().compareTo(ledger.getCommissionAmount()) < 0) {
        throw new BizException("回款金额不能小于佣金金额", ...);
    }

    // 更新流水状态
    DistributionCommissionLedgerEntity update = new DistributionCommissionLedgerEntity();
    update.setId(ledgerId);
    update.setReceivedAmount(request.getReceivedAmount());
    update.setPaymentReferenceNo(request.getPaymentReferenceNo());
    update.setProofUrl(request.getProofUrl());
    update.setReceivedAt(new Date());
    update.setStatus("settleable");
    commissionLedgerMapper.updateByPrimaryKeySelective(update);

    // 自动生成结算单
    Long settlementId = createSettlementForLedger(ledger, operatorUserId);

    // 关联结算单
    DistributionCommissionLedgerEntity link = new DistributionCommissionLedgerEntity();
    link.setId(ledgerId);
    link.setSettlementId(settlementId);
    commissionLedgerMapper.updateByPrimaryKeySelective(link);

    auditLogService.record("commission", ledgerId, "record_receipt", ...);
}
```

## 第二步：创建结算单

支持两种方式：单笔自动创建、批量按时间段归集。

### 单笔自动创建（录入回款时）

```java
private Long createSettlementForLedger(DistributionCommissionLedgerEntity ledger, Long operatorUserId) {
    DistributionSettlementEntity settlement = new DistributionSettlementEntity();
    settlement.setSettlementNo(generateSettlementNo());
    settlement.setDistributorId(ledger.getDistributorId());
    settlement.setPeriodStart(new Date());
    settlement.setPeriodEnd(new Date());
    settlement.setPayableAmount(ledger.getCommissionAmount());
    settlement.setReversedAmount(BigDecimal.ZERO);
    settlement.setActualAmount(ledger.getCommissionAmount());
    settlement.setAuditStatus("pending");
    settlement.setPaymentStatus("pending_audit");
    settlement.setDeleted(0);
    settlementMapper.insertSelective(settlement);

    // 创建明细
    DistributionSettlementItemEntity item = new DistributionSettlementItemEntity();
    item.setSettlementId(settlement.getId());
    item.setCommissionLedgerId(ledger.getId());
    item.setBizOrderId(ledger.getBizOrderId());
    item.setCommissionAmount(ledger.getCommissionAmount());
    item.setStatus("active");
    settlementItemMapper.insertSelective(item);

    return settlement.getId();
}
```

### 批量归集（按渠道+时间段）

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createSettlement(CreateDistributionSettlementRequestDTO request) {
    // 1. 查找可结算的佣金流水
    List<DistributionCommissionLedgerEntity> settleableLedgers =
        commissionLedgerMapper.selectSettleableByDistributorAndPeriod(
            request.getDistributorId(), request.getPeriodStart(), request.getPeriodEnd());

    if (settleableLedgers.isEmpty()) {
        throw new BizException("无可结算佣金流水", ...);
    }

    // 2. 计算总金额
    BigDecimal payableAmount = BigDecimal.ZERO;
    for (DistributionCommissionLedgerEntity ledger : settleableLedgers) {
        payableAmount = payableAmount.add(ledger.getCommissionAmount());
    }

    // 3. 创建结算单
    DistributionSettlementEntity settlement = new DistributionSettlementEntity();
    settlement.setSettlementNo(generateSettlementNo());
    settlement.setDistributorId(request.getDistributorId());
    settlement.setPeriodStart(request.getPeriodStart());
    settlement.setPeriodEnd(request.getPeriodEnd());
    settlement.setPayableAmount(payableAmount);
    settlement.setActualAmount(payableAmount);
    settlement.setAuditStatus("pending");
    settlement.setPaymentStatus("pending_audit");
    settlementMapper.insertSelective(settlement);

    // 4. 创建明细 + 关联流水
    for (DistributionCommissionLedgerEntity ledger : settleableLedgers) {
        DistributionSettlementItemEntity item = new DistributionSettlementItemEntity();
        item.setSettlementId(settlement.getId());
        item.setCommissionLedgerId(ledger.getId());
        item.setBizOrderId(ledger.getBizOrderId());
        item.setCommissionAmount(ledger.getCommissionAmount());
        item.setStatus("active");
        settlementItemMapper.insertSelective(item);

        ledger.setSettlementId(settlement.getId());
        commissionLedgerMapper.updateByPrimaryKeySelective(ledger);
    }

    auditLogService.record("settlement", settlement.getId(), "create", null, settlement, "创建结算批次");
    return settlement.getId();
}
```

## 第三步：审核与打款

结算单的状态流转：

```
PENDING ──► APPROVED ──► PAID
    │
    └──► REJECTED
```

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void approveSettlement(Long id, ApproveDistributionSettlementRequestDTO request) {
    DistributionSettlementEntity settlement = getSettlementEntity(id);

    if (!"pending".equals(settlement.getAuditStatus())) {
        throw new BizException("只有待审核状态可以审批", ...);
    }

    DistributionSettlementEntity update = new DistributionSettlementEntity();
    update.setId(id);
    update.setAuditStatus("approved");
    update.setPaymentStatus("pending_payment");
    update.setUpdatedBy(operatorService.getCurrentOperatorUserId());
    settlementMapper.updateByPrimaryKeySelective(update);

    auditLogService.record("settlement", id, "approve", settlement, getSettlementEntity(id), "审核通过");
}

@Override
@Transactional(rollbackFor = Exception.class)
public void paySettlement(Long id, PayDistributionSettlementRequestDTO request) {
    DistributionSettlementEntity settlement = getSettlementEntity(id);

    if (!"approved".equals(settlement.getAuditStatus())) {
        throw new BizException("只有审核通过的结算单可以打款", ...);
    }

    DistributionSettlementEntity update = new DistributionSettlementEntity();
    update.setId(id);
    update.setPaymentStatus("paid");
    update.setPaidAt(new Date());
    update.setPaymentReferenceNo(request.getPaymentReferenceNo());
    update.setUpdatedBy(operatorService.getCurrentOperatorUserId());
    settlementMapper.updateByPrimaryKeySelective(update);

    // 更新佣金流水状态为已结算
    List<DistributionSettlementItemEntity> items = settlementItemMapper.selectBySettlementId(id);
    for (DistributionSettlementItemEntity item : items) {
        DistributionCommissionLedgerEntity ledgerUpdate = new DistributionCommissionLedgerEntity();
        ledgerUpdate.setId(item.getCommissionLedgerId());
        ledgerUpdate.setStatus("settled");
        commissionLedgerMapper.updateByPrimaryKeySelective(ledgerUpdate);
    }

    auditLogService.record("settlement", id, "pay", settlement, getSettlementEntity(id), "确认打款");
}
```

## 第四步：冲回对结算单的影响

当已结算的佣金被冲回时，需要更新结算单的 `reversed_amount` 和 `actual_amount`：

```java
private void markPaidSettlementReversed(DistributionCommissionLedgerEntity sourceLedger,
                                        DistributionCommissionLedgerEntity reverseLedger) {
    if (sourceLedger.getSettlementId() == null) return;

    DistributionSettlementEntity settlement = settlementMapper.selectByPrimaryKey(sourceLedger.getSettlementId());
    if (settlement == null) return;

    // 更新结算单的冲回金额
    BigDecimal reversedAmount = settlement.getReversedAmount().add(reverseLedger.getCommissionAmount().abs());
    BigDecimal actualAmount = settlement.getPayableAmount().subtract(reversedAmount);

    DistributionSettlementEntity update = new DistributionSettlementEntity();
    update.setId(settlement.getId());
    update.setReversedAmount(reversedAmount);
    update.setActualAmount(actualAmount);
    settlementMapper.updateByPrimaryKeySelective(update);

    // 更新明细状态
    List<DistributionSettlementItemEntity> items = settlementItemMapper.selectBySettlementId(settlement.getId());
    for (DistributionSettlementItemEntity item : items) {
        if (sourceLedger.getId().equals(item.getCommissionLedgerId())) {
            item.setStatus("reversed");
            settlementItemMapper.updateByPrimaryKeySelective(item);
            break;
        }
    }
}
```

## 验证

```sql
-- 1. 录入回款后，检查结算单是否自动创建
SELECT * FROM distribution_settlement WHERE distributor_id = 1;

-- 2. 检查明细
SELECT * FROM distribution_settlement_item WHERE settlement_id = 1;

-- 3. 审核通过
-- 4. 确认打款
-- 5. 检查佣金流水状态是否变为 settled
SELECT status FROM distribution_commission_ledger WHERE id = 1;
```

## 下一步

结算流程有了，但对于受监管行业，还需要合规检查。下一步加入合规记录。

→ [Step 07: 合规检查](07-compliance.md)

---

