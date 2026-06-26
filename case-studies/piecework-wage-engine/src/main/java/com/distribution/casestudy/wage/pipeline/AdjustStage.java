package com.distribution.casestudy.wage.pipeline;

import com.distribution.casestudy.wage.model.WageSettlement;

import java.math.BigDecimal;

/**
 * Pipeline stage: Apply manual adjustments.
 *
 * <p>In the original jewelry ERP, adjustments were made via {@code adjustWage(id, amount, reason)}.
 * This stage allows pre-configured adjustments to be applied automatically during settlement.</p>
 *
 * <p>Common adjustment types:</p>
 * <ul>
 *   <li>Bonus for exceeding quality targets</li>
 *   <li>Deduction for material waste</li>
 *   <li>Overtime premium</li>
 *   <li>Shift differential</li>
 * </ul>
 */
public class AdjustStage implements SettlementStage {

    private final AdjustmentProvider adjustmentProvider;

    public AdjustStage() {
        this(null);
    }

    public AdjustStage(AdjustmentProvider adjustmentProvider) {
        this.adjustmentProvider = adjustmentProvider;
    }

    @Override
    public WageSettlement process(WageSettlement settlement) {
        if (adjustmentProvider != null) {
            AdjustmentResult result = adjustmentProvider.getAdjustment(settlement);
            if (result != null && result.amount() != null) {
                settlement.setAdjustmentAmount(result.amount());
                settlement.setAdjustmentReason(result.reason());
                settlement.calculateFinalAmount();
            }
        }
        return settlement;
    }

    /**
     * Provides adjustment amounts for a settlement.
     */
    @FunctionalInterface
    public interface AdjustmentProvider {
        AdjustmentResult getAdjustment(WageSettlement settlement);
    }

    /**
     * Adjustment result with amount and reason.
     */
    public record AdjustmentResult(BigDecimal amount, String reason) {
    }
}
