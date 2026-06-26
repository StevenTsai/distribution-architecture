package com.distribution.casestudy.wage;

import com.distribution.casestudy.wage.model.PieceworkRecord;
import com.distribution.casestudy.wage.model.WageConfig;
import com.distribution.casestudy.wage.model.WageSettlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Core piecework wage calculator.
 *
 * <p>Extracted and generalized from jewelry ERP {@code BizPieceworkWageServiceImpl}.
 * The original formula was simple: {@code piecePrice * totalQty}.
 * This version introduces configurable multipliers for difficulty and quality.</p>
 *
 * <h3>Formula</h3>
 * <pre>
 * baseWage     = unitPrice × completedQuantity
 * adjustedWage = baseWage × difficultyCoefficient × qualityCoefficient
 * finalAmount  = adjustedWage + adjustmentAmount
 * </pre>
 *
 * <h3>Quality coefficient logic</h3>
 * <ul>
 *   <li>If qualification rate >= qualityThreshold → coefficient = 1.0 (full pay)</li>
 *   <li>If qualification rate < qualityThreshold → coefficient = qualityPenaltyFactor (reduced pay)</li>
 * </ul>
 */
public class WageCalculator {

    /**
     * Calculate wage for a single piecework record.
     *
     * @param record the production record
     * @param config the wage configuration for this process
     * @return a calculated wage settlement (status = PENDING)
     */
    public WageSettlement calculate(PieceworkRecord record, WageConfig config) {
        WageSettlement settlement = new WageSettlement();

        // Map inputs
        settlement.setWorkOrderId(record.getWorkOrderId());
        settlement.setProcessId(record.getProcessId());
        settlement.setWorkerId(record.getWorkerId());
        settlement.setWorkerName(record.getWorkerName());
        settlement.setProcessName(record.getProcessName());
        settlement.setCompletedQuantity(record.getCompletedQuantity());
        settlement.setQualifiedQuantity(record.getQualifiedQuantity());
        settlement.setUnitPrice(config.getUnitPrice());
        settlement.setDifficultyCoefficient(config.getDifficultyCoefficient());

        // Calculate quality coefficient based on actual qualification rate
        BigDecimal qualificationRate = record.getQualificationRate();
        BigDecimal qualityCoeff = config.getEffectiveQualityCoefficient(qualificationRate);
        settlement.setQualityCoefficient(qualityCoeff);

        // Calculate
        settlement.calculateBaseWage();
        settlement.applyCoefficients();
        settlement.calculateFinalAmount();

        // Settlement metadata
        settlement.setSettlementPeriod(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        settlement.setSettlementDate(LocalDate.now());

        return settlement;
    }

    /**
     * Calculate wages for multiple piecework records.
     *
     * @param records list of production records
     * @param configLookup function to get wage config for a process ID
     * @return list of calculated settlements
     */
    public List<WageSettlement> calculateAll(List<PieceworkRecord> records,
                                             ProcessConfigLookup configLookup) {
        List<WageSettlement> settlements = new ArrayList<>();
        for (PieceworkRecord record : records) {
            WageConfig config = configLookup.lookup(record.getProcessId());
            if (config == null) {
                throw new IllegalArgumentException("No wage config found for process: " + record.getProcessId());
            }
            settlements.add(calculate(record, config));
        }
        return settlements;
    }

    /**
     * Summarize total wages for a worker across multiple settlements.
     *
     * @param workerId the worker ID
     * @param settlements list of settlements
     * @return total final amount for this worker
     */
    public BigDecimal summarizeWorker(Long workerId, List<WageSettlement> settlements) {
        return settlements.stream()
                .filter(s -> workerId.equals(s.getWorkerId()))
                .map(s -> s.getFinalAmount() != null ? s.getFinalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Functional interface for looking up wage configuration by process ID.
     */
    @FunctionalInterface
    public interface ProcessConfigLookup {
        WageConfig lookup(Long processId);
    }
}
