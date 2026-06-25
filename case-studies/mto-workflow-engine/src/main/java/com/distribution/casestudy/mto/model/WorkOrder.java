package com.distribution.casestudy.mto.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core work order entity for Make-To-Order production.
 *
 * <p>Abstracts the jewelry-specific {@code BizWorkOrder} entity into a generic
 * MTO work order. Replaces jewelry fields (material, purity, color, styleNo)
 * with generic product fields (productCode, productSpec).</p>
 *
 * <p>Lifecycle: PENDING → SCHEDULED → PRODUCING → PARTIAL_FINISHED → ALL_FINISHED → WAREHOUSED → CLOSED</p>
 *
 * @see WorkOrderStatus
 * @see WorkOrderProcess
 */
public class WorkOrder {

    private Long id;

    /** Auto-generated work order number (e.g. "WO20260625001"). */
    private String workOrderNo;

    /** Reference to the source sales order (optional). */
    private Long salesOrderId;

    /** Customer identifier. */
    private Long customerId;

    /** Customer display name. */
    private String customerName;

    /** Product code / SKU — replaces jewelry "styleNo". */
    private String productCode;

    /** Product specification — replaces jewelry material/purity/color. */
    private Map<String, String> productSpec;

    /** Planned production quantity. */
    private Integer plannedQuantity;

    /** Finished good quantity. */
    private Integer finishedQuantity;

    /** Defective quantity. */
    private Integer defectQuantity;

    /** Process route template ID — defines the sequence of operations. */
    private Long processRouteTemplateId;

    /** Assigned work center. */
    private WorkCenter workCenter;

    /** Assigned worker. */
    private Worker assignedWorker;

    /** Priority level (e.g. "URGENT", "NORMAL", "LOW"). */
    private String priority;

    /** Current lifecycle status. */
    private WorkOrderStatus status;

    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;

    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Child processes (transient, loaded on demand). */
    private List<WorkOrderProcess> processes;

    /** Material requirements (transient, loaded on demand). */
    private List<MaterialRequirement> materials;

    public WorkOrder() {
        this.status = WorkOrderStatus.PENDING;
        this.finishedQuantity = 0;
        this.defectQuantity = 0;
        this.processes = new ArrayList<>();
        this.materials = new ArrayList<>();
    }

    /**
     * Check if this work order is in a modifiable state.
     */
    public boolean isModifiable() {
        return this.status == WorkOrderStatus.PENDING;
    }

    /**
     * Check if all processes are completed.
     */
    public boolean areAllProcessesCompleted() {
        return processes != null && !processes.isEmpty()
                && processes.stream().allMatch(WorkOrderProcess::isCompleted);
    }

    /**
     * Count incomplete processes.
     */
    public long countIncompleteProcesses() {
        if (processes == null) return 0;
        return processes.stream().filter(p -> !p.isCompleted()).count();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkOrderNo() { return workOrderNo; }
    public void setWorkOrderNo(String workOrderNo) { this.workOrderNo = workOrderNo; }

    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public Map<String, String> getProductSpec() { return productSpec; }
    public void setProductSpec(Map<String, String> productSpec) { this.productSpec = productSpec; }

    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public Integer getFinishedQuantity() { return finishedQuantity; }
    public void setFinishedQuantity(Integer finishedQuantity) { this.finishedQuantity = finishedQuantity; }

    public Integer getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(Integer defectQuantity) { this.defectQuantity = defectQuantity; }

    public Long getProcessRouteTemplateId() { return processRouteTemplateId; }
    public void setProcessRouteTemplateId(Long processRouteTemplateId) { this.processRouteTemplateId = processRouteTemplateId; }

    public WorkCenter getWorkCenter() { return workCenter; }
    public void setWorkCenter(WorkCenter workCenter) { this.workCenter = workCenter; }

    public Worker getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(Worker assignedWorker) { this.assignedWorker = assignedWorker; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }

    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDate plannedStartDate) { this.plannedStartDate = plannedStartDate; }

    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDate plannedEndDate) { this.plannedEndDate = plannedEndDate; }

    public LocalDate getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(LocalDate actualStartDate) { this.actualStartDate = actualStartDate; }

    public LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDate actualEndDate) { this.actualEndDate = actualEndDate; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<WorkOrderProcess> getProcesses() { return processes; }
    public void setProcesses(List<WorkOrderProcess> processes) { this.processes = processes; }

    public List<MaterialRequirement> getMaterials() { return materials; }
    public void setMaterials(List<MaterialRequirement> materials) { this.materials = materials; }
}
