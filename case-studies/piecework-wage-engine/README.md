# Case Study: Piecework Wage Calculation Engine

A generic piecework wage calculation engine with a Pipeline pattern, extracted from a production jewelry ERP and generalized for any manufacturing industry.

## What Is This

This case study demonstrates how to build a configurable piecework wage calculation engine. The original jewelry ERP had a simple `piecePrice × quantity` formula. This generic version introduces difficulty and quality coefficients, and uses a Pipeline pattern for the settlement flow.

## Background

The jewelry ERP's `BizPieceworkWageServiceImpl` (142 lines) managed:
- Single process wage calculation
- Batch calculation per work order
- Manual adjustments
- Confirmation workflow
- Monthly summary

The original formula was simple: `wageAmount = piecePrice × totalQty`

This case study extends it to: `wage = unitPrice × quantity × difficultyCoeff × qualityCoeff`

## Formula

```
baseWage     = unitPrice × completedQuantity
adjustedWage = baseWage × difficultyCoefficient × qualityCoefficient
finalAmount  = adjustedWage + adjustmentAmount
```

### Quality Coefficient Logic

| Qualification Rate | Quality Coefficient | Effect |
|-------------------|--------------------|----|
| ≥ 95% (threshold) | 1.0 | Full pay |
| < 95% | 0.8 (penalty factor) | 80% pay |

Configurable per process — different processes can have different thresholds and penalty factors.

## Settlement Pipeline

```
PieceworkRecord → [CalculateStage] → [AdjustStage] → [ConfirmStage] → [SummaryStage]
                  baseWage            adjustments      auto-confirm      worker summary
                  coefficients        (optional)        (criteria-based)  (running total)
```

### Pipeline Stages

| Stage | Purpose | Configurable? |
|-------|---------|--------------|
| `CalculateStage` | Compute base wage and apply coefficients | Fixed logic |
| `AdjustStage` | Apply manual/auto adjustments | Via `AdjustmentProvider` |
| `ConfirmStage` | Auto-confirm if criteria met | Via `AutoConfirmCriteria` |
| `SummaryStage` | Aggregate wages by worker | Side-effect collector |

### Why a Pipeline?

The original jewelry ERP used a linear sequence of method calls. The Pipeline pattern:
- **Extensible**: add tax calculation, compliance check, etc. without modifying existing stages
- **Testable**: each stage can be tested independently
- **Configurable**: different industries use different stage combinations

## Project Structure

```
src/main/java/com/distribution/casestudy/wage/
├── WageCalculator.java              # Core calculation engine
├── WageSettlementPipeline.java      # Pipeline orchestrator
├── model/
│   ├── PieceworkRecord.java         # Production record (work report)
│   ├── WageConfig.java              # Process wage configuration
│   ├── WageSettlement.java          # Finalized settlement record
│   └── WageStatus.java              # Lifecycle status enum
└── pipeline/
    ├── SettlementStage.java         # Stage interface
    ├── CalculateStage.java          # Base wage + coefficients
    ├── AdjustStage.java             # Manual/auto adjustments
    ├── ConfirmStage.java            # Auto-confirmation
    └── SummaryStage.java            # Worker summary aggregation
```

## Key Design Decisions

### 1. Configurable Multipliers

The original had no difficulty/quality coefficients. These were introduced to:
- Reward workers on harder processes (difficulty > 1.0)
- Penalize poor quality output (quality coefficient < 1.0)
- Allow per-process configuration

### 2. BigDecimal Precision

All monetary calculations use `BigDecimal` with:
- `HALF_UP` rounding mode (standard financial rounding)
- Scale 2 for final amounts (currency precision)
- Scale 4 for intermediate calculations (qualification rate)

### 3. Immutable Calculations

`WageSettlement` calculation methods (`calculateBaseWage`, `applyCoefficients`, `calculateFinalAmount`) are explicit steps, not auto-computed on field change. This makes the calculation order clear and debuggable.

### 4. Status Guards

State transitions are guarded:
- Only PENDING settlements can be adjusted or confirmed
- Only CONFIRMED settlements can be marked as paid
- PAID settlements cannot be cancelled

## How to Use

### 1. Configure Wage Rates

```java
WageConfig config = new WageConfig(1L, "Stone Setting", new BigDecimal("5.00"));
config.setDifficultyCoefficient(new BigDecimal("1.2"));   // 20% harder
config.setQualityThreshold(new BigDecimal("0.95"));       // 95% pass rate
config.setQualityPenaltyFactor(new BigDecimal("0.8"));    // 80% pay if below threshold
```

### 2. Calculate a Single Wage

```java
WageCalculator calculator = new WageCalculator();
PieceworkRecord record = new PieceworkRecord(101L, "Alice", "Stone Setting", 100, 98);
WageSettlement settlement = calculator.calculate(record, config);

// settlement.getBaseWage()      = 500.00 (5 × 100)
// settlement.getAdjustedWage()  = 600.00 (500 × 1.2 × 1.0)
// settlement.getFinalAmount()   = 600.00
```

### 3. Process Through Pipeline

```java
WageSettlementPipeline pipeline = new WageSettlementPipeline()
    .addStage(new CalculateStage())
    .addStage(new AdjustStage(s -> new AdjustmentResult(new BigDecimal("50"), "bonus")))
    .addStage(new ConfirmStage())
    .addStage(new SummaryStage());

List<WageSettlement> results = pipeline.processAll(settlements);
```

## Abstraction Mapping

| Jewelry (Original) | Generic (This Case Study) | Industry Examples |
|--------------------|--------------------------|--------------------|
| `BizPieceworkWage` | `WageSettlement` | Any piecework record |
| `BizProcessReport` | `PieceworkRecord` | Work report, time sheet |
| `piecePrice` | `unitPrice` | Rate per piece/unit |
| `masterId` / `masterName` | `workerId` / `workerName` | Employee, operator |
| `goldLoss` / `stoneLoss` | (removed — material loss tracked elsewhere) | — |
| `wageMonth` | `settlementPeriod` | Pay period |
| Simple `piecePrice × qty` | `unitPrice × qty × difficulty × quality` | Configurable multipliers |
| Linear method calls | Pipeline pattern | Extensible stages |

## Related Documents

- [MTO Workflow Engine](../mto-workflow-engine/) — The work order state machine that generates piecework records
- [Spring Events](../spring-events-decoupling/) — Event-driven pattern for cross-module wage notifications
- [Commission Pipeline](../../architecture/commission-pipeline.md) — Similar pipeline pattern for commission calculation

## Source

Extracted from the `jewelry` production ERP system. The original `BizPieceworkWageServiceImpl` was 142 lines with a simple `piecePrice × quantity` formula. This generic version adds configurable multipliers and a Pipeline pattern for extensibility.
