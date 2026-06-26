package com.distribution.casestudy.events.listener;

import com.distribution.casestudy.events.event.WorkOrderFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDate;

/**
 * Listens for work order completion and auto-creates a finished goods inventory record.
 *
 * <p>In the original jewelry ERP, this was a <strong>direct synchronous call</strong>:
 * {@code finishedInventoryService.createInventory(finished)} inside {@code BizWorkOrderServiceImpl.finishWork()}.
 * The production module had a compile-time dependency on the inventory module.</p>
 *
 * <p>This refactored version uses an event-driven pattern:
 * production publishes {@link WorkOrderFinishedEvent}, and this listener
 * (in the inventory module) handles the side effect. The modules are now decoupled.</p>
 *
 * <h3>Before (direct call — tightly coupled)</h3>
 * <pre>
 * // In production module:
 * public void finishWork(Long woId) {
 *     wo.setStatus("ALL_FINISHED");
 *     updateById(wo);
 *     // Direct dependency on inventory module!
 *     finishedInventoryService.createInventory(finished);
 * }
 * </pre>
 *
 * <h3>After (event — decoupled)</h3>
 * <pre>
 * // In production module:
 * public void finishWork(Long woId) {
 *     wo.setStatus("ALL_FINISHED");
 *     updateById(wo);
 *     eventPublisher.publishEvent(new WorkOrderFinishedEvent(this, ...));
 * }
 * // In inventory module (this listener):
 * @EventListener
 * public void onWorkOrderFinished(WorkOrderFinishedEvent event) {
 *     inventoryCallback.createFinishedGoods(...);
 * }
 * </pre>
 */
@Slf4j
public class WorkOrderToInventoryListener {

    private final FinishedGoodsCallback finishedGoodsCallback;

    public WorkOrderToInventoryListener(FinishedGoodsCallback finishedGoodsCallback) {
        this.finishedGoodsCallback = finishedGoodsCallback;
    }

    @EventListener
    @Async("eventAsyncExecutor")
    public void onWorkOrderFinished(WorkOrderFinishedEvent event) {
        try {
            finishedGoodsCallback.createFinishedGoods(
                    event.getWorkOrderId(),
                    event.getWorkOrderNo(),
                    event.getProductId(),
                    event.getProductCode(),
                    event.getFinishedQuantity(),
                    LocalDate.now(),
                    "IN_STOCK"
            );

            log.info("Work order finished → created finished goods: woId={}, qty={}",
                    event.getWorkOrderId(), event.getFinishedQuantity());
        } catch (Exception e) {
            log.error("Failed to create finished goods from work order event: woId={}, error={}",
                    event.getWorkOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Callback interface for finished goods creation.
     * Decouples production module from inventory module.
     */
    public interface FinishedGoodsCallback {
        void createFinishedGoods(Long workOrderId, String workOrderNo,
                                 Long productId, String productCode,
                                 int quantity, LocalDate producedDate,
                                 String status);
    }
}
