package com.distribution.casestudy.wage.model;

/**
 * Piecework wage lifecycle status.
 *
 * <p>Extracted from jewelry ERP {@code WageStatus}. The original had 3 states:
 * PENDING → CONFIRMED → PAID. This version adds CANCELLED for edge cases.</p>
 *
 * <pre>
 * PENDING → CONFIRMED → PAID
 *    ↓
 * CANCELLED
 * </pre>
 */
public enum WageStatus {

    /** Just calculated, awaiting review. */
    PENDING("Pending"),

    /** Reviewed and confirmed by employee/manager. */
    CONFIRMED("Confirmed"),

    /** Payment has been processed. */
    PAID("Paid"),

    /** Cancelled (e.g., work order voided, rework). */
    CANCELLED("Cancelled");

    private final String description;

    WageStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
