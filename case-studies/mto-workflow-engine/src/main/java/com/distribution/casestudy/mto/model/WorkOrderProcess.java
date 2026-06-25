package com.distribution.casestudy.mto.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single process (operation) within a work order's process route.
 *
 * <p>Abstracts the jewelry-specific {@code BizWoProcess} entity.
 * Removes jewelry-specific fields (goldLoss, stoneLoss, piecePrice) and
 * replaces them with generic material loss tracking and unit cost.</p>
 *
 * <p>Examples by industry:</p>
 * <ul>
 *   <li>Jewelry: Casting → Setting → Polishing → Plating</li>
 *   <li>Furniture: Cutting → Sanding → Assembly → Finishing</li>
 *   <li>Electronics: SMT → Reflow → Inspection → Packaging</li>
 * </ul>
 */
public class WorkOrderProcess {

    private Long id;
    private Long workOrderId;

    /** Reference to the master process definition. */
    private Long processDefinitionId;

    /** Denormalized process name for display. */
    private String processName;

    /** Order of this process within the route (1-based). */
    private Integer sortOrder;

    /** Assigned worker. */
    private Worker assignedWorker;

    /** Planned hours for this process. */
    private BigDecimal plannedHours;

    /** Actual hours spent. */
    private BigDecimal actualHours;

    /** Unit cost per piece/unit for this process. */
    private BigDecimal unitCost;

    /** Whether this process requires quality inspection after completion. */
    private Boolean needInspection;

    /** Planned quantity for this process. */
    private Integer plannedQuantity;

    /** Completed quantity (good output). */
    private Integer finishedQuantity;

    /** Qualified quantity after inspection. */
    private Integer qualifiedQuantity;

    /** Rejected quantity after inspection. */
    private Integer unqualifiedQuantity;

    /** Material loss/waste during this process (generic, in the material's unit). */
    private BigDecimal materialLoss;

    /** Acceptable loss rate as a fraction (e.g. 0.05 = 5%). */
    private BigDecimal allowableLossRate;

    /** Process status. */
    private ProcessStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public WorkOrderProcess() {
        this.status = ProcessStatus.PENDING;
        this.finishedQuantity = 0;
        this.qualifiedQuantity = 0;
        this.unqualifiedQuantity = 0;
        this.materialLoss = BigDecimal.ZERO;
    }

    public WorkOrderProcess(Long processDefinitionId, String processName, int sortOrder) {
        this();
        this.processDefinitionId = processDefinitionId;
        this.processName = processName;
        this.sortOrder = sortOrder;
    }

    /**
     * Mark this process as started.
     */
    public void start() {
        if (this.status != ProcessStatus.PENDING) {
            throw new IllegalStateException("Cannot start process in status: " + this.status);
        }
        this.status = ProcessStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Mark this process as completed.
     */
    public void complete() {
        if (this.status != ProcessStatus.IN_PROGRESS && this.status != ProcessStatus.PENDING) {
            throw new IllegalStateException("Cannot complete process in status: " + this.status);
        }
        this.status = ProcessStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * Send this process back to rework.
     */
    public void rework() {
        if (this.status != ProcessStatus.COMPLETED) {
            throw new IllegalStateException("Cannot rework process in status: " + this.status);
        }
        this.status = ProcessStatus.REWORK;
    }

    public boolean isCompleted() {
        return this.status == ProcessStatus.COMPLETED;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public Long getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(Long processDefinitionId) { this.processDefinitionId = processDefinitionId; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Worker getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(Worker assignedWorker) { this.assignedWorker = assignedWorker; }

    public BigDecimal getPlannedHours() { return plannedHours; }
    public void setPlannedHours(BigDecimal plannedHours) { this.plannedHours = plannedHours; }

    public BigDecimal getActualHours() { return actualHours; }
    public void setActualHours(BigDecimal actualHours) { this.actualHours = actualHours; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public Boolean getNeedInspection() { return needInspection; }
    public void setNeedInspection(Boolean needInspection) { this.needInspection = needInspection; }

    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public Integer getFinishedQuantity() { return finishedQuantity; }
    public void setFinishedQuantity(Integer finishedQuantity) { this.finishedQuantity = finishedQuantity; }

    public Integer getQualifiedQuantity() { return qualifiedQuantity; }
    public void setQualifiedQuantity(Integer qualifiedQuantity) { this.qualifiedQuantity = qualifiedQuantity; }

    public Integer getUnqualifiedQuantity() { return unqualifiedQuantity; }
    public void setUnqualifiedQuantity(Integer unqualifiedQuantity) { this.unqualifiedQuantity = unqualifiedQuantity; }

    public BigDecimal getMaterialLoss() { return materialLoss; }
    public void setMaterialLoss(BigDecimal materialLoss) { this.materialLoss = materialLoss; }

    public BigDecimal getAllowableLossRate() { return allowableLossRate; }
    public void setAllowableLossRate(BigDecimal allowableLossRate) { this.allowableLossRate = allowableLossRate; }

    public ProcessStatus getStatus() { return status; }
    public void setStatus(ProcessStatus status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
