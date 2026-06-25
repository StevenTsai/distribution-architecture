package com.distribution.casestudy.mto.model;

/**
 * Represents a production work center (workshop / team / production line).
 *
 * <p>Abstracts the jewelry-specific "workshop + team" into a generic work center model.
 * In different industries this could be a workshop, production line, assembly cell, etc.</p>
 *
 * @param workCenterId   unique identifier
 * @param workCenterName display name
 * @param location       optional physical location
 */
public record WorkCenter(Long workCenterId, String workCenterName, String location) {

    public WorkCenter(Long workCenterId, String workCenterName) {
        this(workCenterId, workCenterName, null);
    }
}
