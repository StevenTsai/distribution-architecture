package com.distribution.casestudy.wage.pipeline;

import com.distribution.casestudy.wage.model.WageSettlement;

/**
 * A single stage in the wage settlement pipeline.
 *
 * <p>Each stage receives a settlement, performs its transformation,
 * and returns the (possibly modified) settlement. If a stage throws,
 * the settlement is dropped from the pipeline.</p>
 */
@FunctionalInterface
public interface SettlementStage {

    /**
     * Process a wage settlement.
     *
     * @param settlement the settlement to process
     * @return the processed settlement
     * @throws Exception if processing fails (settlement will be dropped)
     */
    WageSettlement process(WageSettlement settlement) throws Exception;
}
