package com.distribution.casestudy.wage;

import com.distribution.casestudy.wage.model.PieceworkRecord;
import com.distribution.casestudy.wage.model.WageConfig;
import com.distribution.casestudy.wage.model.WageSettlement;
import com.distribution.casestudy.wage.model.WageStatus;
import com.distribution.casestudy.wage.pipeline.AdjustStage;
import com.distribution.casestudy.wage.pipeline.CalculateStage;
import com.distribution.casestudy.wage.pipeline.ConfirmStage;
import com.distribution.casestudy.wage.pipeline.SummaryStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WageCalculator} and {@link WageSettlementPipeline}.
 *
 * <p>Covers the full wage calculation flow: calculate → adjust → confirm → summary.</p>
 */
class WageCalculatorTest {

    private WageCalculator calculator;
    private WageConfig stoneSettingConfig;
    private WageConfig assemblyConfig;

    @BeforeEach
    void setUp() {
        calculator = new WageCalculator();

        // Jewelry: stone setting is harder (difficulty 1.2), $5/piece
        stoneSettingConfig = new WageConfig(1L, "Stone Setting", new BigDecimal("5.00"));
        stoneSettingConfig.setDifficultyCoefficient(new BigDecimal("1.2"));
        stoneSettingConfig.setQualityThreshold(new BigDecimal("0.95"));
        stoneSettingConfig.setQualityPenaltyFactor(new BigDecimal("0.8"));

        // Furniture: assembly is standard (difficulty 1.0), $15/piece
        assemblyConfig = new WageConfig(2L, "Assembly", new BigDecimal("15.00"));
    }

    // ========== Basic Calculation ==========

    @Nested
    @DisplayName("Basic wage calculation")
    class BasicCalculationTests {

        @Test
        @DisplayName("Standard calculation: unitPrice × quantity")
        void standardCalculation() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);

            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            assertEquals(new BigDecimal("750.00"), settlement.getBaseWage());        // 15 × 50
            assertEquals(0, BigDecimal.ONE.compareTo(settlement.getDifficultyCoefficient()));
            assertEquals(0, BigDecimal.ONE.compareTo(settlement.getQualityCoefficient()));
            assertEquals(new BigDecimal("750.00"), settlement.getAdjustedWage());
            assertEquals(new BigDecimal("750.00"), settlement.getFinalAmount());
            assertEquals(WageStatus.PENDING, settlement.getStatus());
        }

        @Test
        @DisplayName("With difficulty coefficient")
        void withDifficultyCoefficient() {
            PieceworkRecord record = new PieceworkRecord(102L, "Bob", "Stone Setting", 100, 100);

            WageSettlement settlement = calculator.calculate(record, stoneSettingConfig);

            assertEquals(new BigDecimal("500.00"), settlement.getBaseWage());        // 5 × 100
            assertEquals(new BigDecimal("1.2"), settlement.getDifficultyCoefficient());
            assertEquals(new BigDecimal("600.00"), settlement.getAdjustedWage());    // 500 × 1.2
        }

        @Test
        @DisplayName("With quality penalty when below threshold")
        void withQualityPenalty() {
            // 100 completed, 90 qualified (90% < 95% threshold)
            PieceworkRecord record = new PieceworkRecord(102L, "Bob", "Stone Setting", 100, 90);

            WageSettlement settlement = calculator.calculate(record, stoneSettingConfig);

            assertEquals(0, new BigDecimal("0.8").compareTo(settlement.getQualityCoefficient()));
            // baseWage=500, adjustedWage=500×1.2×0.8=480
            assertEquals(new BigDecimal("480.00"), settlement.getAdjustedWage());
        }

        @Test
        @DisplayName("Zero quantity")
        void zeroQuantity() {
            PieceworkRecord record = new PieceworkRecord(103L, "Charlie", "Assembly", 0, 0);

            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            assertEquals(BigDecimal.ZERO.setScale(2), settlement.getBaseWage());
            assertEquals(BigDecimal.ZERO.setScale(2), settlement.getFinalAmount());
        }
    }

    // ========== Batch Calculation ==========

    @Nested
    @DisplayName("Batch calculation")
    class BatchCalculationTests {

        @Test
        @DisplayName("Calculate multiple records with different configs")
        void batchCalculation() {
            PieceworkRecord r1 = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            r1.setProcessId(2L);
            PieceworkRecord r2 = new PieceworkRecord(101L, "Alice", "Stone Setting", 30, 30);
            r2.setProcessId(1L);
            PieceworkRecord r3 = new PieceworkRecord(102L, "Bob", "Assembly", 20, 20);
            r3.setProcessId(2L);
            List<PieceworkRecord> records = List.of(r1, r2, r3);

            List<WageSettlement> settlements = calculator.calculateAll(records,
                    processId -> processId == 1L ? stoneSettingConfig : assemblyConfig);

            assertEquals(3, settlements.size());
            assertEquals(new BigDecimal("750.00"), settlements.get(0).getFinalAmount());   // Alice assembly
            assertEquals(new BigDecimal("180.00"), settlements.get(1).getFinalAmount());   // Alice stone: 5×30×1.2
            assertEquals(new BigDecimal("300.00"), settlements.get(2).getFinalAmount());   // Bob assembly
        }
    }

    // ========== Adjustment ==========

    @Nested
    @DisplayName("Wage adjustment")
    class AdjustmentTests {

        @Test
        @DisplayName("Positive adjustment (bonus)")
        void positiveAdjustment() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            settlement.adjust(new BigDecimal("100.00"), "Quality bonus");

            assertEquals(new BigDecimal("100.00"), settlement.getAdjustmentAmount());
            assertEquals("Quality bonus", settlement.getAdjustmentReason());
            assertEquals(new BigDecimal("850.00"), settlement.getFinalAmount());
        }

        @Test
        @DisplayName("Negative adjustment (deduction)")
        void negativeAdjustment() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            settlement.adjust(new BigDecimal("-50.00"), "Material waste deduction");

            assertEquals(new BigDecimal("700.00"), settlement.getFinalAmount());
        }

        @Test
        @DisplayName("Cannot adjust non-PENDING settlement")
        void cannotAdjustConfirmed() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);
            settlement.confirm();

            assertThrows(IllegalStateException.class,
                    () -> settlement.adjust(new BigDecimal("100"), "late"));
        }
    }

    // ========== Pipeline ==========

    @Nested
    @DisplayName("Pipeline processing")
    class PipelineTests {

        @Test
        @DisplayName("Full pipeline: calculate → adjust → confirm → summary")
        void fullPipeline() {
            SummaryStage summaryStage = new SummaryStage();

            WageSettlementPipeline pipeline = new WageSettlementPipeline()
                    .addStage(new CalculateStage())
                    .addStage(new AdjustStage())  // no provider = no auto-adjustments
                    .addStage(new ConfirmStage()) // no adjustments → auto-confirm
                    .addStage(summaryStage);

            List<PieceworkRecord> records = List.of(
                    new PieceworkRecord(101L, "Alice", "Assembly", 50, 50),
                    new PieceworkRecord(102L, "Bob", "Assembly", 30, 30)
            );

            List<WageSettlement> results = pipeline.processAll(
                    records.stream().map(r -> calculator.calculate(r, assemblyConfig)).toList());

            assertEquals(2, results.size());
            // Auto-confirmed (no adjustments)
            assertEquals(WageStatus.CONFIRMED, results.get(0).getStatus());
            assertEquals(WageStatus.CONFIRMED, results.get(1).getStatus());

            // Summary
            assertEquals(new BigDecimal("750.00"), summaryStage.getWorkerSummary(101L).getTotalAmount());
            assertEquals(new BigDecimal("450.00"), summaryStage.getWorkerSummary(102L).getTotalAmount());
        }

        @Test
        @DisplayName("Pipeline with auto-adjustment")
        void pipelineWithAdjustment() {
            // Auto-adjust: bonus for quality > 99%
            WageSettlementPipeline pipeline = new WageSettlementPipeline()
                    .addStage(new CalculateStage())
                    .addStage(new AdjustStage(settlement -> {
                        if (settlement.getQualifiedQuantity() == settlement.getCompletedQuantity()) {
                            return new AdjustStage.AdjustmentResult(
                                    new BigDecimal("50.00"), "Perfect quality bonus");
                        }
                        return null;
                    }))
                    .addStage(new ConfirmStage(s -> true)); // always auto-confirm for testing

            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement result = pipeline.process(calculator.calculate(record, assemblyConfig));

            assertEquals(new BigDecimal("50.00"), result.getAdjustmentAmount());
            assertEquals(new BigDecimal("800.00"), result.getFinalAmount());
            assertEquals(WageStatus.CONFIRMED, result.getStatus());
        }
    }

    // ========== Status Lifecycle ==========

    @Nested
    @DisplayName("Status lifecycle")
    class StatusLifecycleTests {

        @Test
        @DisplayName("PENDING → CONFIRMED → PAID")
        void happyPath() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            assertEquals(WageStatus.PENDING, settlement.getStatus());

            settlement.confirm();
            assertEquals(WageStatus.CONFIRMED, settlement.getStatus());

            settlement.markPaid();
            assertEquals(WageStatus.PAID, settlement.getStatus());
        }

        @Test
        @DisplayName("Cannot confirm non-PENDING")
        void cannotConfirmTwice() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);
            settlement.confirm();

            assertThrows(IllegalStateException.class, settlement::confirm);
        }

        @Test
        @DisplayName("Cannot pay non-CONFIRMED")
        void cannotPayPending() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            assertThrows(IllegalStateException.class, settlement::markPaid);
        }

        @Test
        @DisplayName("Cannot cancel PAID")
        void cannotCancelPaid() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);
            settlement.confirm();
            settlement.markPaid();

            assertThrows(IllegalStateException.class, settlement::cancel);
        }

        @Test
        @DisplayName("Can cancel PENDING")
        void canCancelPending() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 50, 50);
            WageSettlement settlement = calculator.calculate(record, assemblyConfig);
            settlement.cancel();

            assertEquals(WageStatus.CANCELLED, settlement.getStatus());
        }
    }

    // ========== BigDecimal Precision ==========

    @Nested
    @DisplayName("BigDecimal precision")
    class PrecisionTests {

        @Test
        @DisplayName("Rounding uses HALF_UP with scale 2")
        void roundingPrecision() {
            WageConfig config = new WageConfig(1L, "Test", new BigDecimal("3.33"));
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Test", 100, 100);

            WageSettlement settlement = calculator.calculate(record, config);

            // 3.33 × 100 = 333.00 (exact)
            assertEquals(new BigDecimal("333.00"), settlement.getBaseWage());
        }

        @Test
        @DisplayName("Rounding with fractional result")
        void fractionalRounding() {
            WageConfig config = new WageConfig(1L, "Test", new BigDecimal("3.33"));
            config.setDifficultyCoefficient(new BigDecimal("1.11"));
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Test", 7, 7);

            WageSettlement settlement = calculator.calculate(record, config);

            // 3.33 × 7 = 23.31, × 1.11 = 25.8741 → 25.87
            assertEquals(new BigDecimal("25.87"), settlement.getAdjustedWage());
        }
    }
}
