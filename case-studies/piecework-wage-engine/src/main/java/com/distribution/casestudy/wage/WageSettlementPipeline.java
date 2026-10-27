package com.distribution.casestudy.wage;

import com.distribution.casestudy.wage.model.WageSettlement;
import com.distribution.casestudy.wage.pipeline.SettlementStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline for processing wage settlements through ordered stages.
 *
 * <p>Abstracts the jewelry ERP's linear settlement flow (calculate → adjust → confirm → summary)
 * into an extensible Pipeline pattern. Each stage is a {@link SettlementStage} that can be
 * added, removed, or reordered.</p>
 *
 * <p>Default pipeline: Calculate → Adjust → Confirm → Summary</p>
 *
 * <h3>Why a Pipeline?</h3>
 * <ul>
 *   <li>Extensible: add new stages (e.g., tax calculation, compliance check) without modifying existing code</li>
 *   <li>Testable: each stage can be tested independently</li>
 *   <li>Observable: log each stage's input/output for debugging</li>
 *   <li>Configurable: different industries can use different stage combinations</li>
 * </ul>
 *
 * <h3>Industry examples</h3>
 * <ul>
 *   <li>Standard: Calculate → Adjust → Confirm → Summary</li>
 *   <li>With tax: Calculate → Adjust → TaxDeduction → Confirm → Summary</li>
 *   <li>With compliance: Calculate → Adjust → ComplianceCheck → Confirm → Summary</li>
 * </ul>
 */
public class WageSettlementPipeline {

    private static final Logger log = LoggerFactory.getLogger(WageSettlementPipeline.class);

    private final List<SettlementStage> stages = new ArrayList<>();

    /** 最近一次 {@link #processAll} 中失败的结算单清单。 */
    private final List<SettlementFailure> failures = new ArrayList<>();

    /**
     * Add a stage to the end of the pipeline.
     *
     * @param stage the stage to add
     * @return this pipeline for chaining
     */
    public WageSettlementPipeline addStage(SettlementStage stage) {
        stages.add(stage);
        return this;
    }

    /**
     * Process a settlement through all stages in order.
     *
     * @param settlement the settlement to process
     * @return the fully processed settlement
     * @throws IllegalStateException if any stage fails
     */
    public WageSettlement process(WageSettlement settlement) {
        WageSettlement current = settlement;
        for (int i = 0; i < stages.size(); i++) {
            SettlementStage stage = stages.get(i);
            try {
                current = stage.process(current);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Pipeline stage %d (%s) failed for settlement: %s"
                                .formatted(i, stage.getClass().getSimpleName(), e.getMessage()), e);
            }
        }
        return current;
    }

    /**
     * 批量处理结算单：单条失败不阻断其他记录，失败项被记录到 {@link #getFailures()}
     * 并以 error 级别写日志——结果可见，而不是静默缩减。
     *
     * @param settlements list of settlements to process
     * @return list of successfully processed settlements（失败清单见 {@link #getFailures()}）
     */
    public List<WageSettlement> processAll(List<WageSettlement> settlements) {
        failures.clear();
        List<WageSettlement> results = new ArrayList<>();
        for (WageSettlement settlement : settlements) {
            try {
                results.add(process(settlement));
            } catch (Exception e) {
                failures.add(new SettlementFailure(settlement, e.getMessage()));
                log.error("Pipeline failed for settlement: workOrderId={}, processId={}, workerId={}",
                        settlement.getWorkOrderId(), settlement.getProcessId(),
                        settlement.getWorkerId(), e);
            }
        }
        return results;
    }

    /**
     * 最近一次 {@link #processAll} 的失败清单（成功处理的记录不在此列）。
     */
    public List<SettlementFailure> getFailures() {
        return List.copyOf(failures);
    }

    /**
     * Get the number of stages in the pipeline.
     */
    public int getStageCount() {
        return stages.size();
    }

    /**
     * 一条失败的结算记录：原始结算单 + 失败原因。
     *
     * @param settlement 处理失败的结算单
     * @param message   失败原因
     */
    public record SettlementFailure(WageSettlement settlement, String message) {
    }
}
