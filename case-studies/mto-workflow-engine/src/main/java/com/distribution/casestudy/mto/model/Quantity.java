package com.distribution.casestudy.mto.model;

import java.math.BigDecimal;

/**
 * Immutable value object representing a quantity with a unit of measure.
 *
 * <p>Replaces jewelry-specific weight fields (grams, carats) with a generic
 * quantity model that can express any unit: kg, pcs, meters, liters, etc.</p>
 *
 * @param amount the numeric amount
 * @param unit   the unit of measure (e.g. "kg", "pcs", "m")
 */
public record Quantity(BigDecimal amount, String unit) {

    public Quantity {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit must not be blank");
        }
    }

    public static Quantity of(BigDecimal amount, String unit) {
        return new Quantity(amount, unit);
    }

    public static Quantity kg(BigDecimal amount) {
        return new Quantity(amount, "kg");
    }

    public static Quantity pcs(int amount) {
        return new Quantity(BigDecimal.valueOf(amount), "pcs");
    }

    /**
     * Add two quantities of the same unit.
     *
     * @param other the other quantity
     * @return a new Quantity with the sum
     * @throws IllegalArgumentException if units differ
     */
    public Quantity add(Quantity other) {
        if (!this.unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                    "Cannot add quantities with different units: %s vs %s".formatted(this.unit, other.unit));
        }
        return new Quantity(this.amount.add(other.amount), this.unit);
    }

    /**
     * Subtract another quantity (same unit).
     *
     * @param other the other quantity
     * @return a new Quantity with the difference
     */
    public Quantity subtract(Quantity other) {
        if (!this.unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                    "Cannot subtract quantities with different units: %s vs %s".formatted(this.unit, other.unit));
        }
        return new Quantity(this.amount.subtract(other.amount), this.unit);
    }

    /**
     * Check if this quantity is greater than or equal to another.
     */
    public boolean isGreaterOrEqual(Quantity other) {
        if (!this.unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                    "Cannot compare quantities with different units: %s vs %s".formatted(this.unit, other.unit));
        }
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isZero() {
        return BigDecimal.ZERO.compareTo(amount) == 0;
    }

    @Override
    public String toString() {
        return amount.stripTrailingZeros().toPlainString() + " " + unit;
    }
}
