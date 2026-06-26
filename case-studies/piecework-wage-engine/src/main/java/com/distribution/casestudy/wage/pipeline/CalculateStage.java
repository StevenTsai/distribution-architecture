package com.distribution.casestudy.wage.pipeline;

import com.distribution.casestudy.wage.model.WageSettlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pipeline stage: Calculate base wage and apply coefficients.
 *
 * <p>Computes:</p>
 * <ol>
 *   <li>baseWage = unitPrice × completedQuantity</li>
 *   <li>adjustedWage = baseWage × difficultyCoefficient × qualityCoefficient</li>
 *   <li>finalAmount = adjustedWage</li>
 * </ol>
 *
 * <p>This is the first stage — it transforms raw inputs into a calculated wage amount.</p>
 */
public class CalculateStage implements SettlementStage {

    @Override
    public WageSettlement process(WageSettlement settlement) {
        // Base wage = unitPrice × completedQuantity
        settlement.setBaseWage(
                settlement.getUnitPrice()
                        .multiply(BigDecimal.valueOf(settlement.getCompletedQuantity()))
                        .setScale(2, RoundingMode.HALF_UP)
        );

        // Apply coefficients
        settlement.applyCoefficients();

        // Set final amount (adjustments come in the next stage)
        settlement.calculateFinalAmount();

        return settlement;
    }
}
