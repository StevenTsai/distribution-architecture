package com.distribution.casestudy.mto;

import com.distribution.casestudy.mto.model.MaterialRequirement;
import com.distribution.casestudy.mto.model.Quantity;
import com.distribution.casestudy.mto.model.WorkOrder;
import com.distribution.casestudy.mto.model.WorkOrderStatus;
import com.distribution.casestudy.mto.service.InventoryQueryService;
import com.distribution.casestudy.mto.service.MaterialCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MaterialCheckService} 的测试：齐料 / 缺料两个分支 + 状态守卫。
 *
 * <p>库存查询用内存实现打桩，验证算法本身：剩余需求 = 已领退回后的净需求，
 * 库存 ≥ 剩余需求即为齐料。</p>
 */
class MaterialCheckServiceTest {

    /** 测试专用的内存版库存查询实现。 */
    private static class InMemoryInventory implements InventoryQueryService {

        private final Map<String, Quantity> stock;

        InMemoryInventory(Map<String, Quantity> stock) {
            this.stock = stock;
        }

        @Override
        public Quantity getAvailableStock(String materialCategory, String materialName) {
            return stock.getOrDefault(materialCategory + "/" + materialName,
                    Quantity.of(BigDecimal.ZERO, "pcs"));
        }
    }

    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        workOrder = new WorkOrder();
        workOrder.setWorkOrderNo("WO-100");
        workOrder.getMaterials().add(material("RAW_MATERIAL", "Oak Wood Panel", 10));
        workOrder.getMaterials().add(material("COMPONENT", "M4 Bolt", 50));
    }

    private MaterialRequirement material(String category, String name, int required) {
        return new MaterialRequirement(category, name, Quantity.pcs(required));
    }

    @Test
    @DisplayName("齐料：库存 ≥ 需求 → allSufficient，且 PENDING 可转 MATERIAL_READY")
    void allSufficient() {
        MaterialCheckService service = new MaterialCheckService(new InMemoryInventory(Map.of(
                "RAW_MATERIAL/Oak Wood Panel", Quantity.pcs(12),
                "COMPONENT/M4 Bolt", Quantity.pcs(50)
        )));

        MaterialCheckService.MaterialReadinessResult result = service.checkMaterialReadiness(workOrder);

        assertTrue(result.allSufficient());
        assertEquals(2, result.details().size());
        result.details().forEach(d -> assertTrue(d.sufficient()));

        service.markAsMaterialReady(workOrder);
        assertEquals(WorkOrderStatus.MATERIAL_READY, workOrder.getStatus());
    }

    @Test
    @DisplayName("缺料：库存 < 需求 → allSufficient=false，缺料明细可见，齐套动作被拒")
    void insufficient() {
        MaterialCheckService service = new MaterialCheckService(new InMemoryInventory(Map.of(
                "RAW_MATERIAL/Oak Wood Panel", Quantity.pcs(3),
                "COMPONENT/M4 Bolt", Quantity.pcs(50)
        )));

        MaterialCheckService.MaterialReadinessResult result = service.checkMaterialReadiness(workOrder);

        assertFalse(result.allSufficient());
        // 缺料的那条明细必须可见（而不是只给一个 false）
        assertTrue(result.details().stream()
                .filter(d -> d.materialName().equals("Oak Wood Panel"))
                .allMatch(d -> !d.sufficient()));
        assertEquals(Quantity.pcs(10), result.details().stream()
                .filter(d -> d.materialName().equals("Oak Wood Panel"))
                .findFirst().orElseThrow().remainingNeed());

        assertThrows(IllegalStateException.class, () -> service.markAsMaterialReady(workOrder));
        assertEquals(WorkOrderStatus.PENDING, workOrder.getStatus());
    }

    @Test
    @DisplayName("守卫：非 PENDING 工单不能标记齐套")
    void guardNonPending() {
        MaterialCheckService service = new MaterialCheckService(new InMemoryInventory(Map.of()));
        workOrder.setStatus(WorkOrderStatus.SCHEDULED);

        assertThrows(IllegalStateException.class, () -> service.markAsMaterialReady(workOrder));
        assertEquals(WorkOrderStatus.SCHEDULED, workOrder.getStatus());
    }

    @Test
    @DisplayName("已领料扣减后剩余需求参与齐套判断")
    void remainingNeedConsidersIssuedQuantity() {
        // 木板需求 10，已领 8 → 净需求 2；库存 5 → 齐料
        MaterialRequirement panel = material("RAW_MATERIAL", "Oak Wood Panel", 10);
        panel.setIssuedQuantity(Quantity.pcs(8));
        workOrder.setMaterials(java.util.List.of(panel));

        MaterialCheckService service = new MaterialCheckService(new InMemoryInventory(Map.of(
                "RAW_MATERIAL/Oak Wood Panel", Quantity.pcs(5)
        )));

        MaterialCheckService.MaterialReadinessResult result = service.checkMaterialReadiness(workOrder);
        assertTrue(result.allSufficient());
        assertEquals(Quantity.pcs(2), result.details().get(0).remainingNeed());
    }
}
