package com.distribution.casestudy.mto.service;

import com.distribution.casestudy.mto.model.MaterialRequirement;
import com.distribution.casestudy.mto.model.Quantity;
import com.distribution.casestudy.mto.model.WorkOrder;
import com.distribution.casestudy.mto.model.WorkOrderStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for checking material readiness before production can begin.
 *
 * <p>Extracted from jewelry-specific {@code BizMaterialCheckServiceImpl}.
 * The original implementation was tightly coupled to gold and stone inventory
 * tables. This version uses the generic {@link InventoryQueryService} interface
 * to support any material type.</p>
 *
 * <p>Algorithm (same as original):</p>
 * <ol>
 *   <li>Load all material requirements for the work order</li>
 *   <li>For each material, compute remaining need: required - issued + returned</li>
 *   <li>Query available stock via InventoryQueryService</li>
 *   <li>Compare available >= remaining need</li>
 *   <li>If all sufficient, work order can transition to MATERIAL_READY</li>
 * </ol>
 */
public class MaterialCheckService {

    private final InventoryQueryService inventoryQueryService;

    public MaterialCheckService(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    /**
     * Check if all materials for a work order are available.
     *
     * @param workOrder the work order to check
     * @return check result with details for each material
     */
    public MaterialReadinessResult checkMaterialReadiness(WorkOrder workOrder) {
        List<MaterialCheckDetail> details = new ArrayList<>();
        boolean allSufficient = true;

        for (MaterialRequirement material : workOrder.getMaterials()) {
            Quantity remaining = material.getRemainingNeed();
            Quantity available = inventoryQueryService.getAvailableStock(
                    material.getMaterialCategory(), material.getMaterialName());

            boolean sufficient = available.isGreaterOrEqual(remaining);

            details.add(new MaterialCheckDetail(
                    material.getMaterialCategory(),
                    material.getMaterialName(),
                    remaining,
                    available,
                    sufficient
            ));

            if (!sufficient) {
                allSufficient = false;
            }
        }

        return new MaterialReadinessResult(allSufficient, details);
    }

    /**
     * Check material readiness and transition work order to MATERIAL_READY if all sufficient.
     *
     * @param workOrder the work order to check and transition
     * @throws IllegalStateException if work order is not in PENDING status
     * @throws IllegalStateException if materials are not all sufficient
     */
    public void markAsMaterialReady(WorkOrder workOrder) {
        if (workOrder.getStatus() != WorkOrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING work orders can be marked as material ready, current: " + workOrder.getStatus());
        }

        MaterialReadinessResult result = checkMaterialReadiness(workOrder);
        if (!result.allSufficient()) {
            throw new IllegalStateException("Materials not fully available, cannot mark as ready");
        }

        workOrder.setStatus(WorkOrderStatus.MATERIAL_READY);
    }

    /**
     * Result of a material readiness check.
     */
    public record MaterialReadinessResult(boolean allSufficient, List<MaterialCheckDetail> details) {
    }

    /**
     * Detail for a single material's readiness check.
     */
    public record MaterialCheckDetail(
            String materialCategory,
            String materialName,
            Quantity remainingNeed,
            Quantity available,
            boolean sufficient
    ) {
    }
}
