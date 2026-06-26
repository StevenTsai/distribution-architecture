package com.distribution.casestudy.wage.pipeline;

import com.distribution.casestudy.wage.model.WageSettlement;
import com.distribution.casestudy.wage.model.WageStatus;

/**
 * Pipeline stage: Auto-confirm settlements that meet criteria.
 *
 * <p>In the original jewelry ERP, confirmation was a manual API call ({@code confirmWage(id)}).
 * This stage can auto-confirm settlements based on configurable criteria.</p>
 *
 * <p>Auto-confirm criteria examples:</p>
 * <ul>
 *   <li>Amount below a threshold (e.g., < $1000) — low risk, auto-approve</li>
 *   <li>No adjustments — standard calculation, no manual override</li>
 *   <li>High quality rate (e.g., >= 99%) — no quality issues</li>
 * </ul>
 */
public class ConfirmStage implements SettlementStage {

    private final AutoConfirmCriteria criteria;

    public ConfirmStage() {
        this(settlement -> settlement.getAdjustmentAmount() == null
                || settlement.getAdjustmentAmount().signum() == 0);
    }

    public ConfirmStage(AutoConfirmCriteria criteria) {
        this.criteria = criteria;
    }

    @Override
    public WageSettlement process(WageSettlement settlement) {
        if (settlement.getStatus() == WageStatus.PENDING && criteria.shouldAutoConfirm(settlement)) {
            settlement.confirm();
        }
        return settlement;
    }

    /**
     * Criteria for automatic confirmation.
     */
    @FunctionalInterface
    public interface AutoConfirmCriteria {
        boolean shouldAutoConfirm(WageSettlement settlement);
    }
}
