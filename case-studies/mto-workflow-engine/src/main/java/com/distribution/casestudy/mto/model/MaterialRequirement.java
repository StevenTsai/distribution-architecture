package com.distribution.casestudy.mto.model;

import java.math.BigDecimal;

/**
 * Material requirement for a work order.
 *
 * <p>Abstracts the jewelry-specific {@code BizWoMaterial} into a generic model.
 * Replaces gold/stone material types with a configurable {@code materialCategory}
 * and uses {@link Quantity} for unified quantity handling.</p>
 *
 * <p>In jewelry: GOLD (weight-based), STONE (quantity-based).
 * In furniture: WOOD, FABRIC, HARDWARE.
 * In electronics: PCB, COMPONENT, SOLDER.</p>
 */
public class MaterialRequirement {

    private Long id;
    private Long workOrderId;

    /** Material category — e.g. "RAW_MATERIAL", "COMPONENT", "SUB_ASSEMBLY". */
    private String materialCategory;

    /** Specific material name/SKU — e.g. "Oak Wood Panel", "M4 Bolt". */
    private String materialName;

    /** Material specification — e.g. "2000x1000x18mm". */
    private String specification;

    /** Required quantity. */
    private Quantity requiredQuantity;

    /** Already issued quantity. */
    private Quantity issuedQuantity;

    /** Already returned quantity (unused material sent back). */
    private Quantity returnedQuantity;

    /** Material readiness status. */
    private String status;

    public MaterialRequirement() {
    }

    public MaterialRequirement(String materialCategory, String materialName, Quantity requiredQuantity) {
        this.materialCategory = materialCategory;
        this.materialName = materialName;
        this.requiredQuantity = requiredQuantity;
        this.issuedQuantity = Quantity.of(BigDecimal.ZERO, requiredQuantity.unit());
        this.returnedQuantity = Quantity.of(BigDecimal.ZERO, requiredQuantity.unit());
        this.status = "PENDING";
    }

    /**
     * Calculate remaining quantity needed: required - issued + returned.
     */
    public Quantity getRemainingNeed() {
        return requiredQuantity.subtract(issuedQuantity).add(returnedQuantity);
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public String getMaterialCategory() { return materialCategory; }
    public void setMaterialCategory(String materialCategory) { this.materialCategory = materialCategory; }

    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }

    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }

    public Quantity getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(Quantity requiredQuantity) { this.requiredQuantity = requiredQuantity; }

    public Quantity getIssuedQuantity() { return issuedQuantity; }
    public void setIssuedQuantity(Quantity issuedQuantity) { this.issuedQuantity = issuedQuantity; }

    public Quantity getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(Quantity returnedQuantity) { this.returnedQuantity = returnedQuantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
