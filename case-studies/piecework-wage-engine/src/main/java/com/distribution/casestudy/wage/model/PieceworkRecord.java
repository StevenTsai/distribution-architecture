package com.distribution.casestudy.wage.model;

import java.math.BigDecimal;

/**
 * A single piecework production record (work report / time sheet entry).
 *
 * <p>Abstracts the jewelry-specific {@code BizProcessReport} entity.
 * Removes jewelry-specific fields (goldLoss, stoneLoss, girdleCode) and
 * keeps the generic piecework concepts.</p>
 *
 * <p>In jewelry: a worker reports completing N pieces of a specific process
 * (e.g., "set 50 stones"). In furniture: "assembled 10 tables".</p>
 */
public class PieceworkRecord {

    private Long id;
    private Long workOrderId;
    private Long processId;
    private Long workerId;
    private String workerName;
    private String processName;

    /** Number of pieces completed in this report. */
    private int completedQuantity;

    /** Number of pieces that passed quality inspection. */
    private int qualifiedQuantity;

    /** Number of pieces that failed quality inspection. */
    private int unqualifiedQuantity;

    /** Hours spent on this work. */
    private BigDecimal workHours;

    /** When this work was reported. */
    private java.time.LocalDateTime reportedAt;

    public PieceworkRecord() {
    }

    /**
     * 构造一条报工记录，并在构造期校验数量合法性。
     *
     * @throws IllegalArgumentException 如果完工数为负、合格数为负、或合格数大于完工数
     */
    public PieceworkRecord(Long workerId, String workerName, String processName,
                           int completedQuantity, int qualifiedQuantity) {
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
        this.workerId = workerId;
        this.workerName = workerName;
        this.processName = processName;
        this.completedQuantity = completedQuantity;
        this.qualifiedQuantity = qualifiedQuantity;
        this.unqualifiedQuantity = completedQuantity - qualifiedQuantity;
    }

    /**
     * Quality pass rate as a fraction (0.0 to 1.0).
     */
    public BigDecimal getQualificationRate() {
        if (completedQuantity == 0) return BigDecimal.ONE;
        return BigDecimal.valueOf(qualifiedQuantity)
                .divide(BigDecimal.valueOf(completedQuantity), 4, java.math.RoundingMode.HALF_UP);
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

    public int getUnqualifiedQuantity() { return unqualifiedQuantity; }
    public void setUnqualifiedQuantity(int unqualifiedQuantity) { this.unqualifiedQuantity = unqualifiedQuantity; }

    public BigDecimal getWorkHours() { return workHours; }
    public void setWorkHours(BigDecimal workHours) { this.workHours = workHours; }

    public java.time.LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(java.time.LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
}
