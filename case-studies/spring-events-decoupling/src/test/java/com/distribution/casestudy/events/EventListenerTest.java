package com.distribution.casestudy.events;

import com.distribution.casestudy.events.event.DeliveryCompleteEvent;
import com.distribution.casestudy.events.event.QualityDefectEvent;
import com.distribution.casestudy.events.event.WorkOrderFinishedEvent;
import com.distribution.casestudy.events.listener.DeliveryToReceivableListener;
import com.distribution.casestudy.events.listener.QualityToReworkListener;
import com.distribution.casestudy.events.listener.WorkOrderToInventoryListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Spring Event listeners.
 *
 * <p>Tests each listener in isolation using callback captures to verify
 * the correct side effects are triggered by each event.</p>
 */
class EventListenerTest {

    private DeliveryToReceivableListener deliveryListener;
    private WorkOrderToInventoryListener inventoryListener;
    private QualityToReworkListener reworkListener;

    // Captures for callback verification
    private final List<ReceivableCapture> receivableCaptures = new ArrayList<>();
    private final List<FinishedGoodsCapture> finishedGoodsCaptures = new ArrayList<>();
    private final List<ReworkOrderCapture> reworkCaptures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        deliveryListener = new DeliveryToReceivableListener(
                (orderId, orderNo, customerId, amount, receivedAmount, outstandingAmount, dueDate, status) ->
                        receivableCaptures.add(new ReceivableCapture(orderId, orderNo, customerId, amount, dueDate, status))
        );

        inventoryListener = new WorkOrderToInventoryListener(
                (workOrderId, workOrderNo, productId, productCode, quantity, producedDate, status) ->
                        finishedGoodsCaptures.add(new FinishedGoodsCapture(workOrderId, workOrderNo, productCode, quantity, status))
        );

        reworkListener = new QualityToReworkListener(
                (originalWorkOrderId, originalWorkOrderNo, processName, defectType, defectDescription, quantity, recommendedAction) ->
                        reworkCaptures.add(new ReworkOrderCapture(originalWorkOrderId, defectType, processName, quantity, recommendedAction))
        );
    }

    // ========== Delivery → Receivable ==========

    @Test
    @DisplayName("Delivery event creates receivable with correct fields")
    void onDeliveryComplete_createsReceivable() {
        DeliveryCompleteEvent event = new DeliveryCompleteEvent(
                this, 100L, 200L, "ORD-2024-001", 300L, new BigDecimal("5000.00"));

        deliveryListener.onDeliveryComplete(event);

        assertEquals(1, receivableCaptures.size());
        ReceivableCapture r = receivableCaptures.get(0);
        assertEquals(200L, r.orderId);
        assertEquals("ORD-2024-001", r.orderNo);
        assertEquals(300L, r.customerId);
        assertEquals(new BigDecimal("5000.00"), r.amount);
        assertEquals("PENDING", r.status);
        assertEquals(LocalDate.now().plusDays(30), r.dueDate);
    }

    @Test
    @DisplayName("Delivery event handles null amount gracefully")
    void onDeliveryComplete_nullAmount() {
        DeliveryCompleteEvent event = new DeliveryCompleteEvent(
                this, 100L, 200L, "ORD-002", 300L, null);

        deliveryListener.onDeliveryComplete(event);

        assertEquals(1, receivableCaptures.size());
        assertEquals(BigDecimal.ZERO, receivableCaptures.get(0).amount);
    }

    @Test
    @DisplayName("Delivery event listener swallows exceptions (fire-and-forget)")
    void onDeliveryComplete_exceptionSwallowed() {
        DeliveryToReceivableListener failingListener = new DeliveryToReceivableListener(
                (a, b, c, d, e, f, g, h) -> { throw new RuntimeException("DB down"); }
        );
        DeliveryCompleteEvent event = new DeliveryCompleteEvent(
                this, 100L, 200L, "ORD-003", 300L, BigDecimal.ONE);

        // Should NOT throw
        assertDoesNotThrow(() -> failingListener.onDeliveryComplete(event));
    }

    // ========== Work Order → Inventory ==========

    @Test
    @DisplayName("Work order finished event creates finished goods record")
    void onWorkOrderFinished_createsInventory() {
        WorkOrderFinishedEvent event = new WorkOrderFinishedEvent(
                this, 500L, "WO-2024-001", 600L, "PROD-001", 100, 2, LocalDate.now());

        inventoryListener.onWorkOrderFinished(event);

        assertEquals(1, finishedGoodsCaptures.size());
        FinishedGoodsCapture fg = finishedGoodsCaptures.get(0);
        assertEquals(500L, fg.workOrderId);
        assertEquals("WO-2024-001", fg.workOrderNo);
        assertEquals("PROD-001", fg.productCode);
        assertEquals(100, fg.quantity);
        assertEquals("IN_STOCK", fg.status);
    }

    @Test
    @DisplayName("Work order finished event handles exceptions gracefully")
    void onWorkOrderFinished_exceptionSwallowed() {
        WorkOrderToInventoryListener failingListener = new WorkOrderToInventoryListener(
                (a, b, c, d, e, f, g) -> { throw new RuntimeException("Inventory service down"); }
        );
        WorkOrderFinishedEvent event = new WorkOrderFinishedEvent(
                this, 500L, "WO-002", 600L, "PROD-002", 50, 0, LocalDate.now());

        assertDoesNotThrow(() -> failingListener.onWorkOrderFinished(event));
    }

    // ========== Quality Defect → Rework ==========

    @Test
    @DisplayName("Quality defect event creates rework order")
    void onQualityDefect_createsReworkOrder() {
        QualityDefectEvent event = new QualityDefectEvent(
                this, 700L, 500L, "WO-2024-001",
                "Stone Setting", "MISALIGNED", "Stone not centered in prong setting",
                5, "RE-SET");

        reworkListener.onQualityDefect(event);

        assertEquals(1, reworkCaptures.size());
        ReworkOrderCapture rw = reworkCaptures.get(0);
        assertEquals(500L, rw.workOrderId);
        assertEquals("MISALIGNED", rw.defectType);
        assertEquals("Stone Setting", rw.processName);
        assertEquals(5, rw.quantity);
        assertEquals("RE-SET", rw.recommendedAction);
    }

    @Test
    @DisplayName("Quality defect event handles exceptions gracefully")
    void onQualityDefect_exceptionSwallowed() {
        QualityToReworkListener failingListener = new QualityToReworkListener(
                (a, b, c, d, e, f, g) -> { throw new RuntimeException("Production service down"); }
        );
        QualityDefectEvent event = new QualityDefectEvent(
                this, 700L, 500L, "WO-003",
                "Polishing", "SCRATCH", "Surface scratch", 3, "RE-POLISH");

        assertDoesNotThrow(() -> failingListener.onQualityDefect(event));
    }

    // ========== Capture records ==========

    record ReceivableCapture(Long orderId, String orderNo, Long customerId,
                             BigDecimal amount, LocalDate dueDate, String status) {}
    record FinishedGoodsCapture(Long workOrderId, String workOrderNo,
                                String productCode, int quantity, String status) {}
    record ReworkOrderCapture(Long workOrderId, String defectType,
                              String processName, int quantity, String recommendedAction) {}
}
