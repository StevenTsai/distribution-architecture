package com.distribution.casestudy.mto.model;

/**
 * Work order lifecycle status.
 *
 * <p>Extracted from jewelry ERP {@code WorkOrderStatus}, generalized for any MTO industry.</p>
 *
 * <pre>
 * State Machine:
 *
 *   [new] ──create──→ PENDING ──schedule──→ SCHEDULED ──startWork──→ PRODUCING
 *                        │                                                │
 *                        │ markAsMaterialReady                            │ finishProcess (partial)
 *                        ↓                                                ↓
 *                   MATERIAL_READY ──schedule──→ SCHEDULED      PARTIAL_FINISHED
 *                                                                   │
 *                                               ┌───────────────────┘
 *                                               │ finishProcess (all done) / finishWork
 *                                               ↓
 *                                          ALL_FINISHED ──→ WAREHOUSED ──→ CLOSED
 *                                               │                            ↑
 *                                               └──────────closeWork─────────┘
 *
 *   [any except CLOSED/VOID] ──voidWork──→ VOID
 * </pre>
 */
public enum WorkOrderStatus {

    /** Just created, awaiting scheduling. */
    PENDING("Pending"),

    /** Resources assigned, ready to start. */
    SCHEDULED("Scheduled"),

    /** All materials available, ready to schedule. */
    MATERIAL_READY("Material Ready"),

    /** Work in progress. */
    PRODUCING("Producing"),

    /** Some processes completed, others still in progress. */
    PARTIAL_FINISHED("Partial Finished"),

    /** All processes completed. */
    ALL_FINISHED("All Finished"),

    /** Finished goods moved to warehouse. */
    WAREHOUSED("Warehoused"),

    /** Work order archived / closed. */
    CLOSED("Closed"),

    /** Work order cancelled. */
    VOID("Void");

    private final String description;

    WorkOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
