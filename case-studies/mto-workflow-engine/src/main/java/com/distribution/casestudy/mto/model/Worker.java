package com.distribution.casestudy.mto.model;

/**
 * Represents a worker/operator assigned to a work order or process.
 *
 * <p>Abstracts the jewelry-specific "master" concept into a generic worker model.
 * In different industries this could be an assembly worker, machine operator,
 * tailor, carpenter, etc.</p>
 *
 * @param workerId   unique worker identifier
 * @param workerName display name
 * @param skillType  optional skill category (e.g. "welding", "assembly", "QC")
 */
public record Worker(Long workerId, String workerName, String skillType) {

    public Worker(Long workerId, String workerName) {
        this(workerId, workerName, null);
    }
}
