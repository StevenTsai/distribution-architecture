# From Jewelry to Generic: Piecework Wage Engine

This document explains the design decisions when extracting the piecework wage calculation from the jewelry ERP into a generic engine.

## Original System

The jewelry ERP's wage calculation lived in `BizPieceworkWageServiceImpl` (142 lines). It was straightforward:

```java
// Original formula (actual code)
int totalQty = reports.stream().mapToInt(BizProcessReport::getReportQty).sum();
BigDecimal wageAmount = process.getPiecePrice().multiply(BigDecimal.valueOf(totalQty));
```

The settlement flow was linear method calls:
1. `calculateWage()` — compute wage
2. `adjustWage()` — manual adjustment
3. `confirmWage()` — status change
4. `getMonthlySummary()` — SQL aggregation

## What Changed

### 1. Formula: Simple → Multiplied

**Original:** `wage = piecePrice × totalQty`

**Generic:** `wage = unitPrice × quantity × difficultyCoeff × qualityCoeff`

**Why:** The original formula treated all processes equally. In reality:
- Stone setting is harder than polishing (difficulty coefficient)
- Poor quality output should be penalized (quality coefficient)
- Different industries have different multiplier needs

**What was added:**
- `WageConfig.difficultyCoefficient` — process difficulty multiplier
- `WageConfig.qualityThreshold` — quality pass rate threshold
- `WageConfig.qualityPenaltyFactor` — penalty when below threshold

### 2. Settlement: Linear → Pipeline

**Original:** Sequential method calls in `BizPieceworkWageServiceImpl`

**Generic:** `WageSettlementPipeline` with pluggable `SettlementStage` instances

**Why:** The original couldn't easily add new processing steps. If you wanted to add tax calculation, you had to modify the service. The Pipeline pattern makes it extensible.

### 3. Worker: "Master" → Generic Worker

**Original:** `masterId`, `masterName` (jewelry craftsman)

**Generic:** `workerId`, `workerName` (any worker/employee)

**Why:** "Master" is jewelry terminology. Generic terms work for any industry.

### 4. Material Loss: Removed

**Original:** `goldLoss`, `stoneLoss` on `BizProcessReport`

**Generic:** Not included

**Why:** Material loss tracking belongs in a separate domain (inventory/material management), not in wage calculation. The original conflated two concerns.

### 5. Precision: Implicit → Explicit

**Original:** `BigDecimal` operations without explicit scale or rounding mode

**Generic:** `setScale(2, RoundingMode.HALF_UP)` on all monetary values

**Why:** The original relied on database column precision (`DECIMAL(10,2)`) to handle rounding. Explicit rounding in code is more predictable and testable.

## What Was Preserved

1. **WageStatus lifecycle** — PENDING → CONFIRMED → PAID (same as original)
2. **Adjustment mechanism** — `finalAmount = wageAmount + adjustmentAmount`
3. **Monthly settlement period** — `yyyy-MM` format
4. **Worker summary aggregation** — group by worker, sum final amounts
5. **BigDecimal for all monetary values** — no floating point

## What Was Added

1. **`WageConfig`** — Configurable wage parameters per process (didn't exist in original)
2. **`WageSettlementPipeline`** — Extensible pipeline pattern (original was linear)
3. **`SettlementStage` interface** — Pluggable processing stages
4. **`CalculateStage`** — Explicit calculation stage (original was inline in service)
5. **`AdjustStage` with `AdjustmentProvider`** — Auto-adjustment capability (original was manual only)
6. **`ConfirmStage` with `AutoConfirmCriteria`** — Auto-confirmation (original was always manual)
7. **`SummaryStage`** — In-memory summary collection (original was SQL query)

## Pipeline Extensibility Examples

### Standard Manufacturing
```java
pipeline.addStage(new CalculateStage())
        .addStage(new AdjustStage())
        .addStage(new ConfirmStage())
        .addStage(new SummaryStage());
```

### With Tax Calculation
```java
pipeline.addStage(new CalculateStage())
        .addStage(new AdjustStage())
        .addStage(new TaxDeductionStage())  // custom stage
        .addStage(new ConfirmStage())
        .addStage(new SummaryStage());
```

### With Compliance Check
```java
pipeline.addStage(new CalculateStage())
        .addStage(new AdjustStage())
        .addStage(new ComplianceCheckStage())  // custom stage
        .addStage(new ConfirmStage())
        .addStage(new SummaryStage());
```

## Lessons Learned

1. **Simple formulas are fine for v1:** The jewelry system's `piecePrice × quantity` formula worked for years. Don't over-engineer early.

2. **Coefficients add complexity:** Difficulty and quality coefficients require configuration management, threshold decisions, and edge case handling. Only add them when there's a real business need.

3. **Pipeline > linear for extensibility:** If you anticipate adding processing steps, start with a Pipeline. Retrofitting is harder than building it right the first time.

4. **Separate concerns:** Material loss tracking (goldLoss/stoneLoss) doesn't belong in wage calculation. Keep domains clean during extraction.

5. **Explicit precision:** Always specify `setScale()` and `RoundingMode` on `BigDecimal` operations. Don't rely on database columns to handle rounding.
