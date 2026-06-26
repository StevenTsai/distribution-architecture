package com.distribution.casestudy.mto.service;

import com.distribution.casestudy.mto.config.ProcessRouteConfig;
import com.distribution.casestudy.mto.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Core state machine for MTO work order lifecycle management.
 *
 * <p>Extracted and generalized from jewelry ERP {@code BizWorkOrderServiceImpl}.
 * The original implementation embedded state transition logic directly in service
 * methods with inline guards. This version makes the state machine explicit and
 * testable without database dependencies.</p>
 *
 * <p>Key patterns preserved from the original:</p>
 * <ul>
 *   <li>Guard-checked transitions — each method validates current state before transitioning</li>
 *   <li>Process completion auto-detection — when a process finishes, checks if ALL are done</li>
 *   <li>Event publishing — side effects (inventory, notifications) via events, not direct calls</li>
 *   <li>Immutable state objects where possible</li>
 * </ul>
 *
 * <p>State transitions:</p>
 * <pre>
 * create()         → PENDING
 * schedule()       → SCHEDULED  (from PENDING or MATERIAL_READY)
 * startWork()      → PRODUCING  (from SCHEDULED)
 * finishProcess()  → PARTIAL_FINISHED or ALL_FINISHED (from PRODUCING or PARTIAL_FINISHED)
 * finishWork()     → ALL_FINISHED (from PRODUCING or PARTIAL_FINISHED)
 * closeWork()      → CLOSED (from ALL_FINISHED or WAREHOUSED)
 * voidWork()       → VOID (from any except CLOSED or VOID)
 * </pre>
 */
public class WorkOrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderStateMachine.class);

    private final ProcessRouteConfig processRouteConfig;

    public WorkOrderStateMachine(ProcessRouteConfig processRouteConfig) {
        this.processRouteConfig = processRouteConfig;
    }

    // ========== Work Order Lifecycle ==========

    /**
     * Create a new work order with processes from a named route.
     *
     * @param workOrderNo pre-generated work order number
     * @param routeName   process route name from YAML config
     * @return the created work order in PENDING status
     */
    public WorkOrder create(String workOrderNo, String routeName) {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNo(workOrderNo);
        wo.setStatus(WorkOrderStatus.PENDING);

        // Build processes from YAML route config
        // Note: workOrderId will be null until persisted. Callers must set it after saving.
        List<WorkOrderProcess> processes = processRouteConfig.buildProcesses(routeName);
        wo.setProcesses(processes);

        log.info("Created work order: {} with route '{}'", workOrderNo, routeName);
        return wo;
    }

    /**
     * Create a work order with manually specified processes.
     */
    public WorkOrder create(String workOrderNo, List<WorkOrderProcess> processes) {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNo(workOrderNo);
        wo.setStatus(WorkOrderStatus.PENDING);
        // Note: workOrderId will be null until persisted. Callers must set it after saving.
        for (int i = 0; i < processes.size(); i++) {
            processes.get(i).setSortOrder(i + 1);
        }
        wo.setProcesses(processes);

        log.info("Created work order: {} with {} processes", workOrderNo, processes.size());
        return wo;
    }

    /**
     * Assign resources and transition to SCHEDULED.
     *
     * <p>From the original: assigns workshop, team, and master.
     * Generalized to assign a WorkCenter and Worker.</p>
     *
     * @param workOrder  the work order
     * @param workCenter production work center
     * @param worker     assigned worker
     */
    public void schedule(WorkOrder workOrder, WorkCenter workCenter, Worker worker) {
        assertStatus(workOrder, WorkOrderStatus.PENDING, WorkOrderStatus.MATERIAL_READY);

        workOrder.setWorkCenter(workCenter);
        workOrder.setAssignedWorker(worker);
        workOrder.setStatus(WorkOrderStatus.SCHEDULED);

        log.info("Work order {} scheduled at {} by {}",
                workOrder.getWorkOrderNo(),
                workCenter.workCenterName(),
                worker.workerName());
    }

    /**
     * Start production — transition from SCHEDULED to PRODUCING.
     *
     * @param workOrder the work order
     */
    public void startWork(WorkOrder workOrder) {
        assertStatus(workOrder, WorkOrderStatus.SCHEDULED);

        workOrder.setStatus(WorkOrderStatus.PRODUCING);
        workOrder.setActualStartDate(LocalDate.now());

        log.info("Work order {} started production", workOrder.getWorkOrderNo());
    }

    /**
     * Mark a single process as completed and auto-detect work order completion.
     *
     * <p>This is the key pattern from the original: when a process finishes,
     * we check ALL sibling processes. If none are incomplete, the work order
     * advances to ALL_FINISHED automatically.</p>
     *
     * @param workOrder the work order
     * @param processId the process ID to complete
     */
    public void finishProcess(WorkOrder workOrder, Long processId) {
        assertStatus(workOrder, WorkOrderStatus.PRODUCING, WorkOrderStatus.PARTIAL_FINISHED);

        WorkOrderProcess process = findProcess(workOrder, processId);
        if (process == null) {
            throw new IllegalArgumentException("Process not found: " + processId);
        }

        process.complete();
        process.setFinishedAt(LocalDateTime.now());

        // Auto-detect: check if ALL processes are now completed
        long incompleteCount = workOrder.countIncompleteProcesses();
        if (incompleteCount == 0) {
            workOrder.setStatus(WorkOrderStatus.ALL_FINISHED);
            workOrder.setActualEndDate(LocalDate.now());
            log.info("All processes completed, work order {} auto-finished", workOrder.getWorkOrderNo());
        } else {
            if (workOrder.getStatus() != WorkOrderStatus.PARTIAL_FINISHED) {
                workOrder.setStatus(WorkOrderStatus.PARTIAL_FINISHED);
                log.info("Partial completion, {} processes remaining for work order {}",
                        incompleteCount, workOrder.getWorkOrderNo());
            }
        }
    }

    /**
     * Manually finish all remaining work — transition to ALL_FINISHED.
     *
     * <p>In the original, this also triggers finished goods inventory creation
     * via direct service call. Here we just do the state transition;
     * side effects should be handled by event listeners.</p>
     *
     * @param workOrder the work order
     */
    public void finishWork(WorkOrder workOrder) {
        assertStatus(workOrder, WorkOrderStatus.PRODUCING, WorkOrderStatus.PARTIAL_FINISHED);

        // Mark all incomplete processes as completed
        for (WorkOrderProcess p : workOrder.getProcesses()) {
            if (!p.isCompleted()) {
                p.complete();
                p.setFinishedAt(LocalDateTime.now());
            }
        }

        workOrder.setStatus(WorkOrderStatus.ALL_FINISHED);
        workOrder.setActualEndDate(LocalDate.now());

        log.info("Work order {} manually finished", workOrder.getWorkOrderNo());
    }

    /**
     * Close/archive the work order.
     *
     * @param workOrder the work order
     */
    public void closeWork(WorkOrder workOrder) {
        assertStatus(workOrder, WorkOrderStatus.ALL_FINISHED, WorkOrderStatus.WAREHOUSED);

        workOrder.setStatus(WorkOrderStatus.CLOSED);
        log.info("Work order {} closed", workOrder.getWorkOrderNo());
    }

    /**
     * Cancel/void the work order.
     *
     * @param workOrder the work order
     * @param reason    cancellation reason
     */
    public void voidWork(WorkOrder workOrder, String reason) {
        if (workOrder.getStatus() == WorkOrderStatus.CLOSED) {
            throw new IllegalStateException("Cannot void a closed work order");
        }
        if (workOrder.getStatus() == WorkOrderStatus.VOID) {
            throw new IllegalStateException("Work order is already void");
        }

        workOrder.setStatus(WorkOrderStatus.VOID);
        workOrder.setRemark(reason);
        log.warn("Work order {} voided: {}", workOrder.getWorkOrderNo(), reason);
    }

    // ========== Process Route Management ==========

    /**
     * Reorder processes within a work order.
     *
     * @param workOrder      the work order
     * @param sortedProcessIds process IDs in desired order
     */
    public void reorderProcesses(WorkOrder workOrder, List<Long> sortedProcessIds) {
        for (int i = 0; i < sortedProcessIds.size(); i++) {
            WorkOrderProcess process = findProcess(workOrder, sortedProcessIds.get(i));
            if (process != null) {
                process.setSortOrder(i + 1);
            }
        }
        // Sort the in-memory list
        workOrder.getProcesses().sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
    }

    // ========== Internal Helpers ==========

    /**
     * Assert that the work order is in one of the allowed statuses.
     */
    private void assertStatus(WorkOrder workOrder, WorkOrderStatus... allowed) {
        for (WorkOrderStatus s : allowed) {
            if (workOrder.getStatus() == s) return;
        }
        StringBuilder sb = new StringBuilder("Cannot perform operation: work order is ");
        sb.append(workOrder.getStatus());
        sb.append(", expected one of: ");
        for (int i = 0; i < allowed.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(allowed[i]);
        }
        throw new IllegalStateException(sb.toString());
    }

    private WorkOrderProcess findProcess(WorkOrder workOrder, Long processId) {
        return workOrder.getProcesses().stream()
                .filter(p -> processId.equals(p.getId()))
                .findFirst()
                .orElse(null);
    }
}
