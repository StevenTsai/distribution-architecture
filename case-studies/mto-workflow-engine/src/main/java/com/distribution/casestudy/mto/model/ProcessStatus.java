package com.distribution.casestudy.mto.model;

/**
 * Per-process lifecycle status within a work order.
 *
 * <p>Each work order contains an ordered list of processes (operations).
 * This enum tracks the completion status of individual processes.</p>
 */
public enum ProcessStatus {

    /** Not yet started. */
    PENDING("Pending"),

    /** Work in progress on this process. */
    IN_PROGRESS("In Progress"),

    /** Process completed successfully. */
    COMPLETED("Completed"),

    /** Process needs to be redone due to quality issues. */
    REWORK("Rework");

    private final String description;

    ProcessStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
