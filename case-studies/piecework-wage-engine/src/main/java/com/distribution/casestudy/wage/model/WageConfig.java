package com.distribution.casestudy.wage.model;

import java.math.BigDecimal;

/**
 * Configuration for wage calculation of a specific process.
 *
 * <p>Abstracts the jewelry-specific {@code BizWoProcess.piecePrice} and introduces
 * configurable multipliers that didn't exist in the original. The original only had
 * {@code piecePrice * completedQty}. This generic version adds difficulty and quality
 * coefficients as configurable multipliers.</p>
 *
 * <p>Formula: {@code wage = unitPrice × quantity × difficultyCoeff × qualityCoeff}</p>
 *
 * <p>Industry examples:</p>
 * <ul>
 *   <li>Jewelry: casting $2/piece, setting $5/piece (higher difficulty)</li>
 *   <li>Furniture: sanding $1.5/piece, assembly $3/piece</li>
 *   <li>Electronics: SMT placement $0.1/piece, inspection $0.5/piece</li>
 * </ul>
 */
public class WageConfig {

    /** Unique config ID. */
    private Long id;

    /** Process definition ID this config applies to. */
    private Long processId;

    /** Process name for display. */
    private String processName;

    /** Unit price per piece (e.g., $2.50/piece). */
    private BigDecimal unitPrice;

    /**
     * Difficulty coefficient (default 1.0).
     * Multiplier for harder processes. E.g., 1.5 = 50% harder than baseline.
     */
    private BigDecimal difficultyCoefficient;

    /**
     * Quality coefficient threshold (default 1.0).
     * If qualification rate >= this threshold, full quality coefficient applies.
     * If below, a reduced coefficient is used.
     */
    private BigDecimal qualityThreshold;

    /**
     * Penalty factor when quality is below threshold (default 0.8).
     * E.g., 0.8 = 80% of normal wage when quality is poor.
     */
    private BigDecimal qualityPenaltyFactor;

    public WageConfig() {
        this.difficultyCoefficient = BigDecimal.ONE;
        this.qualityThreshold = new BigDecimal("0.95");
        this.qualityPenaltyFactor = new BigDecimal("0.8");
    }

    public WageConfig(Long processId, String processName, BigDecimal unitPrice) {
        this();
        this.processId = processId;
        this.processName = processName;
        this.unitPrice = unitPrice;
    }

    /**
     * Get the effective quality coefficient based on actual qualification rate.
     *
     * @param qualificationRate actual qualification rate (0.0 to 1.0)
     * @return quality coefficient: 1.0 if rate >= threshold, penalty factor otherwise
     */
    public BigDecimal getEffectiveQualityCoefficient(BigDecimal qualificationRate) {
        if (qualificationRate.compareTo(qualityThreshold) >= 0) {
            return BigDecimal.ONE;
        }
        return qualityPenaltyFactor;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDifficultyCoefficient() { return difficultyCoefficient; }
    public void setDifficultyCoefficient(BigDecimal difficultyCoefficient) { this.difficultyCoefficient = difficultyCoefficient; }

    public BigDecimal getQualityThreshold() { return qualityThreshold; }
    public void setQualityThreshold(BigDecimal qualityThreshold) { this.qualityThreshold = qualityThreshold; }

    public BigDecimal getQualityPenaltyFactor() { return qualityPenaltyFactor; }
    public void setQualityPenaltyFactor(BigDecimal qualityPenaltyFactor) { this.qualityPenaltyFactor = qualityPenaltyFactor; }
}
