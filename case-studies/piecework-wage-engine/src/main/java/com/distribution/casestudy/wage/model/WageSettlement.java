package com.distribution.casestudy.wage.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * A finalized piecework wage settlement record.
 *
 * <p>Abstracts the jewelry-specific {@code BizPieceworkWage} entity.
 * Adds explicit coefficient tracking that didn't exist in the original.</p>
 *
 * <p>Lifecycle: PENDING → CONFIRMED → PAID (or CANCELLED)</p>
 *
 * <p>Industry examples:</p>
 * <ul>
 *   <li>Jewelry: worker 101, process "Stone Setting", 500 pieces @ $5/piece, difficulty 1.2</li>
 *   <li>Furniture: worker 202, process "Assembly", 30 tables @ $15/table, difficulty 1.0</li>
 *   <li>Electronics: worker 303, process "SMT", 1000 boards @ $0.1/board, difficulty 0.8</li>
 * </ul>
 */
public class WageSettlement {

    private Long id;
    private Long workOrderId;
    private Long processId;
    private Long workerId;
    private String workerName;
    private String processName;

    // --- Calculation inputs ---
    private int completedQuantity;
    private int qualifiedQuantity;
    private BigDecimal unitPrice;
    private BigDecimal difficultyCoefficient;
    private BigDecimal qualityCoefficient;

    // --- Calculation results ---
    /** Base wage = unitPrice × completedQuantity. */
    private BigDecimal baseWage;

    /** Adjusted wage = baseWage × difficultyCoeff × qualityCoeff. */
    private BigDecimal adjustedWage;

    /** Manual adjustment amount (positive or negative). */
    private BigDecimal adjustmentAmount;
    private String adjustmentReason;

    /** Final amount = adjustedWage + adjustmentAmount. */
    private BigDecimal finalAmount;

    // --- Settlement metadata ---
    /** Settlement period (e.g., "2026-06"). */
    private String settlementPeriod;

    /** Settlement date. */
    private LocalDate settlementDate;

    private WageStatus status;

    public WageSettlement() {
        this.status = WageStatus.PENDING;
        this.adjustmentAmount = BigDecimal.ZERO;
        this.difficultyCoefficient = BigDecimal.ONE;
        this.qualityCoefficient = BigDecimal.ONE;
    }

    /**
     * 计算基础工资：baseWage = unitPrice × completedQuantity。
     *
     * <p>不在中途舍入——金额在 {@link #calculateFinalAmount()} 一次性舍入到
     * 2 位小数，避免「先截断再乘系数」的双重舍入误差。</p>
     *
     * @throws IllegalArgumentException 如果数量为负或合格数大于完工数
     */
    public void calculateBaseWage() {
        validateQuantities();
        this.baseWage = unitPrice.multiply(BigDecimal.valueOf(completedQuantity));
    }

    /**
     * 应用难度与质量系数：adjustedWage = baseWage × difficultyCoefficient × qualityCoefficient。
     *
     * <p>同样不在中途舍入，保留全精度，统一在最终金额处舍入。</p>
     */
    public void applyCoefficients() {
        this.adjustedWage = baseWage
                .multiply(difficultyCoefficient)
                .multiply(qualityCoefficient);
    }

    /**
     * 计算最终金额：finalAmount = adjustedWage + adjustmentAmount，
     * 仅在此处统一 HALF_UP 舍入到 2 位小数（货币精度）。
     */
    public void calculateFinalAmount() {
        this.finalAmount = adjustedWage
                .add(adjustmentAmount != null ? adjustmentAmount : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 追加一笔调整（累加语义，不是覆盖）。
     *
     * <p>多次调用会累加：先 adjust(+50) 再 adjust(+30)，adjustmentAmount 为 +80。
     * 覆盖语义会静默吞掉前一笔调整，是典型的结算事故来源。</p>
     *
     * @param amount 本笔调整金额（正数 = 奖励，负数 = 扣减）
     * @param reason 本笔调整原因（多笔调整的原因以「; 」追加记录）
     * @throws IllegalArgumentException 如果 amount 为 null
     * @throws IllegalStateException    如果结算单不是 PENDING 状态
     */
    public void adjust(BigDecimal amount, String reason) {
        if (status != WageStatus.PENDING) {
            throw new IllegalStateException("Cannot adjust settlement in status: " + status);
        }
        if (amount == null) {
            throw new IllegalArgumentException("adjustment amount must not be null");
        }
        this.adjustmentAmount = (this.adjustmentAmount != null ? this.adjustmentAmount : BigDecimal.ZERO)
                .add(amount);
        this.adjustmentReason = (this.adjustmentReason == null || this.adjustmentReason.isBlank())
                ? reason
                : this.adjustmentReason + "; " + reason;
        calculateFinalAmount();
    }

    /**
     * 校验数量字段的合法性：完工数、合格数均不能为负，且合格数不能超过完工数。
     */
    private void validateQuantities() {
        if (completedQuantity < 0) {
            throw new IllegalArgumentException(
                    "completedQuantity must not be negative: " + completedQuantity);
        }
        if (qualifiedQuantity < 0) {
            throw new IllegalArgumentException(
                    "qualifiedQuantity must not be negative: " + qualifiedQuantity);
        }
        if (qualifiedQuantity > completedQuantity) {
            throw new IllegalArgumentException(
                    "qualifiedQuantity (%d) must not exceed completedQuantity (%d)"
                            .formatted(qualifiedQuantity, completedQuantity));
        }
    }

    /**
     * Confirm the settlement.
     *
     * @throws IllegalStateException if not in PENDING status
     */
    public void confirm() {
        if (status != WageStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm settlement in status: " + status);
        }
        this.status = WageStatus.CONFIRMED;
    }

    /**
     * Mark as paid.
     *
     * @throws IllegalStateException if not in CONFIRMED status
     */
    public void markPaid() {
        if (status != WageStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot mark as paid, current status: " + status);
        }
        this.status = WageStatus.PAID;
    }

    /**
     * Cancel the settlement.
     */
    public void cancel() {
        if (status == WageStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a paid settlement");
        }
        this.status = WageStatus.CANCELLED;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }

    public Long getWorkerId() { return workerId; }
    public void setWorkerId(Long workerId) { this.workerId = workerId; }

    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public int getCompletedQuantity() { return completedQuantity; }
    public void setCompletedQuantity(int completedQuantity) { this.completedQuantity = completedQuantity; }

    public int getQualifiedQuantity() { return qualifiedQuantity; }
    public void setQualifiedQuantity(int qualifiedQuantity) { this.qualifiedQuantity = qualifiedQuantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDifficultyCoefficient() { return difficultyCoefficient; }
    public void setDifficultyCoefficient(BigDecimal difficultyCoefficient) { this.difficultyCoefficient = difficultyCoefficient; }

    public BigDecimal getQualityCoefficient() { return qualityCoefficient; }
    public void setQualityCoefficient(BigDecimal qualityCoefficient) { this.qualityCoefficient = qualityCoefficient; }

    public BigDecimal getBaseWage() { return baseWage; }
    public void setBaseWage(BigDecimal baseWage) { this.baseWage = baseWage; }

    public BigDecimal getAdjustedWage() { return adjustedWage; }
    public void setAdjustedWage(BigDecimal adjustedWage) { this.adjustedWage = adjustedWage; }

    public BigDecimal getAdjustmentAmount() { return adjustmentAmount; }
    public void setAdjustmentAmount(BigDecimal adjustmentAmount) { this.adjustmentAmount = adjustmentAmount; }

    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }

    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }

    public String getSettlementPeriod() { return settlementPeriod; }
    public void setSettlementPeriod(String settlementPeriod) { this.settlementPeriod = settlementPeriod; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public WageStatus getStatus() { return status; }
    public void setStatus(WageStatus status) { this.status = status; }
}
