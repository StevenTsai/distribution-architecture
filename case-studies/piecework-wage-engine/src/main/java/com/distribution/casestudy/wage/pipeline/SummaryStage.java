package com.distribution.casestudy.wage.pipeline;

import com.distribution.casestudy.wage.model.WageSettlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pipeline stage: Aggregate wages by worker for summary reporting.
 *
 * <p>In the original jewelry ERP, summary was a separate SQL query.
 * This stage maintains an in-memory running total per worker.</p>
 *
 * <p>This stage doesn't modify the settlement — it collects summary data
 * as a side effect. The summary can be retrieved after processing all settlements.</p>
 */
public class SummaryStage implements SettlementStage {

    private final Map<Long, WorkerSummary> summaries = new ConcurrentHashMap<>();

    @Override
    public WageSettlement process(WageSettlement settlement) {
        summaries.merge(
                settlement.getWorkerId(),
                new WorkerSummary(
                        settlement.getWorkerId(),
                        settlement.getWorkerName(),
                        settlement.getCompletedQuantity(),
                        settlement.getFinalAmount()
                ),
                (existing, newEntry) -> existing.merge(newEntry)
        );
        return settlement;
    }

    /**
     * Get the summary for a specific worker.
     */
    public WorkerSummary getWorkerSummary(Long workerId) {
        return summaries.get(workerId);
    }

    /**
     * Get all worker summaries.
     */
    public Map<Long, WorkerSummary> getAllSummaries() {
        return Map.copyOf(summaries);
    }

    /**
     * Reset summaries (for testing).
     */
    public void reset() {
        summaries.clear();
    }

    /**
     * Aggregated wage summary for a worker.
     */
    public static class WorkerSummary {
        private final Long workerId;
        private final String workerName;
        private int totalQuantity;
        private BigDecimal totalAmount;

        public WorkerSummary(Long workerId, String workerName, int totalQuantity, BigDecimal totalAmount) {
            this.workerId = workerId;
            this.workerName = workerName;
            this.totalQuantity = totalQuantity;
            this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        }

        public WorkerSummary merge(WorkerSummary other) {
            this.totalQuantity += other.totalQuantity;
            this.totalAmount = this.totalAmount.add(other.totalAmount).setScale(2, RoundingMode.HALF_UP);
            return this;
        }

        public Long getWorkerId() { return workerId; }
        public String getWorkerName() { return workerName; }
        public int getTotalQuantity() { return totalQuantity; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }
}
