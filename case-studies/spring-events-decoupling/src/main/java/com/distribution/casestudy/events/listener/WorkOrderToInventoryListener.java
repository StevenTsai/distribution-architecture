package com.distribution.casestudy.events.listener;

import com.distribution.casestudy.events.event.WorkOrderFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onWorkOrderFinished(WorkOrderFinishedEvent event) {
 *     inventoryCallback.createFinishedGoods(...);
 * }
 * </pre>
 *
 * <p><b>Precondition:</b> the event must be published inside an active transaction —
 * AFTER_COMMIT listeners do not fire for non-transactional publishers.</p>
 */
@Slf4j
public class WorkOrderToInventoryListener {

    private final FinishedGoodsCallback finishedGoodsCallback;

    public WorkOrderToInventoryListener(FinishedGoodsCallback finishedGoodsCallback) {
        this.finishedGoodsCallback = finishedGoodsCallback;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("eventAsyncExecutor")
    public void onWorkOrderFinished(WorkOrderFinishedEvent event) {
        try {
            /* 入库日期取事件携带的完工日期，而非监听线程的 now()——异步延迟不得使日期漂移 */
            finishedGoodsCallback.createFinishedGoods(
                    event.getWorkOrderId(),
                    event.getWorkOrderNo(),
                    event.getProductId(),
                    event.getProductCode(),
                    event.getFinishedQuantity(),
                    event.getFinishedDate(),
                    "IN_STOCK"
            );

            log.info("Work order finished → created finished goods: woId={}, qty={}",
                    event.getWorkOrderId(), event.getFinishedQuantity());
        } catch (Exception e) {
            /* 发后即忘，但绝不静默：带全量业务键落 error 日志，供人工补偿/对账 */
            log.error("创建成品入库失败，需人工补偿/对账：workOrderId={}, workOrderNo={}, productCode={}, qty={}, error={}",
                    event.getWorkOrderId(), event.getWorkOrderNo(), event.getProductCode(),
                    event.getFinishedQuantity(), e.getMessage(), e);
        }
    }

    /**
     * Callback interface for finished goods creation.
     * Decouples production module from inventory module.
     *
     * <p><b>Idempotency contract:</b> implementations must be idempotent on
     * {@code workOrderId} (e.g., unique index + existence check). The same
     * event may be delivered more than once in production; the callback owns
     * deduplication.</p>
     */
    public interface FinishedGoodsCallback {
        void createFinishedGoods(Long workOrderId, String workOrderNo,
                                 Long productId, String productCode,
                                 int quantity, LocalDate producedDate,
                                 String status);
    }
}
