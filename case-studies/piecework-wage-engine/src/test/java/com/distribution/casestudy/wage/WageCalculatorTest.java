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
            // 中途不舍入：500.00 × 1.2 = 600.000（全精度保留）
            assertEquals(0, new BigDecimal("600.00").compareTo(settlement.getAdjustedWage()));
        }

        @Test
        @DisplayName("With quality penalty when below threshold")
        void withQualityPenalty() {
            // 100 completed, 90 qualified (90% < 95% threshold)
            PieceworkRecord record = new PieceworkRecord(102L, "Bob", "Stone Setting", 100, 90);

            WageSettlement settlement = calculator.calculate(record, stoneSettingConfig);

            assertEquals(0, new BigDecimal("0.8").compareTo(settlement.getQualityCoefficient()));
            // baseWage=500, adjustedWage=500×1.2×0.8=480（中途不舍入，compareTo 断言）
            assertEquals(0, new BigDecimal("480.00").compareTo(settlement.getAdjustedWage()));
            // 统一在最终金额处舍入
            assertEquals(new BigDecimal("480.00"), settlement.getFinalAmount());
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

        @Test
        @DisplayName("多笔调整累加：AdjustStage 自动 +50 后人工 +30，合计 80 而非覆盖为 30")
        void multipleAdjustmentsAccumulate() {
            WageSettlement settlement = calculator.calculate(
                    new PieceworkRecord(101L, "Alice", "Assembly", 50, 50), assemblyConfig);

            // 场景还原：流水线 AdjustStage 先自动追加一笔 50 元奖励
            new AdjustStage(s -> new AdjustStage.AdjustmentResult(
                    new BigDecimal("50.00"), "Perfect quality bonus")).process(settlement);

            // 事后人工再追加 30 元（若 adjust 是覆盖语义，这笔 50 会被静默吞掉）
            settlement.adjust(new BigDecimal("30.00"), "Manual correction");

            assertEquals(new BigDecimal("80.00"), settlement.getAdjustmentAmount());
            assertEquals("Perfect quality bonus; Manual correction", settlement.getAdjustmentReason());
            assertEquals(new BigDecimal("830.00"), settlement.getFinalAmount()); // 750 + 80
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
        @DisplayName("中途不舍入，finalAmount 统一 HALF_UP 到 2 位")
        void fractionalRounding() {
            WageConfig config = new WageConfig(1L, "Test", new BigDecimal("3.33"));
            config.setDifficultyCoefficient(new BigDecimal("1.11"));
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Test", 7, 7);

            WageSettlement settlement = calculator.calculate(record, config);

            // 3.33 × 7 = 23.31, × 1.11 = 25.8741 —— 中间不舍入，保留全精度
            assertEquals(0, new BigDecimal("25.8741").compareTo(settlement.getAdjustedWage()));
            // 统一在最终金额处 HALF_UP 舍入 → 25.87
            assertEquals(new BigDecimal("25.87"), settlement.getFinalAmount());
        }
    }

    // ========== Settlement Period ==========

    @Nested
    @DisplayName("结算期归属")
    class SettlementPeriodTests {

        @Test
        @DisplayName("6 月报工、7 月（任意时间）计算 → 归属 2026-06 期")
        void juneReportBelongsToJune() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 10, 10);
            record.setReportedAt(java.time.LocalDateTime.of(2026, 6, 30, 23, 50));

            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            assertEquals("2026-06", settlement.getSettlementPeriod());
        }

        @Test
        @DisplayName("reportedAt 缺失时退化为按当天归属")
        void fallbackToToday() {
            PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Assembly", 10, 10);

            WageSettlement settlement = calculator.calculate(record, assemblyConfig);

            assertEquals(
                    java.time.LocalDate.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                    settlement.getSettlementPeriod());
        }
    }

    // ========== Input Validation ==========

    @Nested
    @DisplayName("输入校验")
    class ValidationTests {

        @Test
        @DisplayName("完工数为负 → IllegalArgumentException")
        void negativeCompletedRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PieceworkRecord(1L, "Alice", "Assembly", -1, 0));
        }

        @Test
        @DisplayName("合格数为负 → IllegalArgumentException")
        void negativeQualifiedRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PieceworkRecord(1L, "Alice", "Assembly", 10, -1));
        }

        @Test
        @DisplayName("合格数大于完工数 → IllegalArgumentException")
        void qualifiedExceedingCompletedRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PieceworkRecord(1L, "Alice", "Assembly", 10, 11));
        }

        @Test
        @DisplayName("边界值 0/0 与 10/10 合法")
        void boundaryValuesAccepted() {
            assertDoesNotThrow(() -> new PieceworkRecord(1L, "Alice", "Assembly", 0, 0));
            assertDoesNotThrow(() -> new PieceworkRecord(1L, "Alice", "Assembly", 10, 10));
        }

        @Test
        @DisplayName("结算单经 setter 注入负完工数 → calculateBaseWage 拒绝")
        void settlementRejectsNegativeCompleted() {
            WageSettlement settlement = calculator.calculate(
                    new PieceworkRecord(1L, "Alice", "Assembly", 10, 10), assemblyConfig);
            settlement.setCompletedQuantity(-5);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, settlement::calculateBaseWage);
            assertTrue(e.getMessage().contains("completedQuantity"));
        }

        @Test
        @DisplayName("结算单合格数大于完工数 → calculateBaseWage 拒绝")
        void settlementRejectsQualifiedExceedingCompleted() {
            WageSettlement settlement = calculator.calculate(
                    new PieceworkRecord(1L, "Alice", "Assembly", 10, 10), assemblyConfig);
            settlement.setQualifiedQuantity(11);

            assertThrows(IllegalArgumentException.class, settlement::calculateBaseWage);
        }
    }

    // ========== Pipeline Failure Visibility ==========

    @Nested
    @DisplayName("流水线失败可见性")
    class PipelineFailureTests {

        @Test
        @DisplayName("一条坏记录不阻断其他记录，且失败清单可见")
        void badRecordVisibleAndNotBlocking() {
            WageSettlementPipeline pipeline = new WageSettlementPipeline()
                    .addStage(new CalculateStage());

            WageSettlement good = calculator.calculate(
                    new PieceworkRecord(101L, "Alice", "Assembly", 10, 10), assemblyConfig);
            WageSettlement bad = calculator.calculate(
                    new PieceworkRecord(102L, "Bob", "Assembly", 10, 10), assemblyConfig);
            bad.setCompletedQuantity(-1); // 绕过构造校验注入坏数据

            List<WageSettlement> results = pipeline.processAll(List.of(good, bad));

            // 好记录照常处理
            assertEquals(1, results.size());
            assertEquals(new BigDecimal("150.00"), results.get(0).getFinalAmount());

            // 失败记录可见，而不是被静默吞掉
            assertEquals(1, pipeline.getFailures().size());
            WageSettlementPipeline.SettlementFailure failure = pipeline.getFailures().get(0);
            assertSame(bad, failure.settlement());
            assertTrue(failure.message().contains("completedQuantity must not be negative"));
        }

        @Test
        @DisplayName("getFailures 只反映最近一次 processAll")
        void failuresResetPerRun() {
            WageSettlementPipeline pipeline = new WageSettlementPipeline()
                    .addStage(new CalculateStage());

            WageSettlement bad = calculator.calculate(
                    new PieceworkRecord(102L, "Bob", "Assembly", 10, 10), assemblyConfig);
            bad.setCompletedQuantity(-1);
            pipeline.processAll(List.of(bad));
            assertEquals(1, pipeline.getFailures().size());

            List<WageSettlement> results = pipeline.processAll(List.of(calculator.calculate(
                    new PieceworkRecord(101L, "Alice", "Assembly", 10, 10), assemblyConfig)));

            assertEquals(1, results.size());
            assertTrue(pipeline.getFailures().isEmpty());
        }
    }
}
