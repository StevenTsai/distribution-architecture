package com.distribution.casestudy.wage.pipeline;

import com.distribution.casestudy.wage.model.WageSettlement;

/**
 * Pipeline stage: Calculate base wage and apply coefficients.
 *
 * <p>计算逻辑复用 {@link WageSettlement} 上的三个显式步骤，不在本类再实现一遍公式
 * （此前这里内联了一份 baseWage 公式，与模型层形成双实现，两处会各自漂移）：</p>
 * <ol>
 *   <li>baseWage = unitPrice × completedQuantity（{@code calculateBaseWage()}）</li>
 *   <li>adjustedWage = baseWage × difficultyCoefficient × qualityCoefficient（{@code applyCoefficients()}）</li>
 *   <li>finalAmount = adjustedWage（{@code calculateFinalAmount()}，调整项在下一阶段追加）</li>
 * </ol>
 *
 * <p>This is the first stage — it transforms raw inputs into a calculated wage amount.</p>
 */
public class CalculateStage implements SettlementStage {

    @Override
    public WageSettlement process(WageSettlement settlement) {
        settlement.calculateBaseWage();
        settlement.applyCoefficients();
        settlement.calculateFinalAmount();
        return settlement;
    }
}
