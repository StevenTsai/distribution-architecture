package com.distribution.casestudy.events.listener;

import com.distribution.casestudy.events.event.DeliveryCompleteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Listens for delivery completion and auto-creates an accounts receivable record.
 *
 * <p>Extracted from jewelry ERP {@code DeliveryEventListener}. The original listener
 * was in the {@code jewelry-finance} module and created a {@code BizReceivable}
 * record. This generalized version uses an {@code AccountsReceivableCallback}
 * interface to decouple from specific database implementations.</p>
 *
 * <h3>Key patterns from the original (with hardening)</h3>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} — fires only after the
 *       publisher's transaction commits. A plain {@code @EventListener + @Async} pair can
 *       race the publisher's transaction: the async thread persists the receivable before
 *       commit, and a rollback leaves a phantom record. AFTER_COMMIT removes that window.</li>
 *   <li>{@code @Async("eventAsyncExecutor")} — fire-and-forget, non-blocking</li>
 *   <li>try/catch — failures are logged with full business keys and an explicit
 *       "manual compensation required" marker, never propagated to the publisher</li>
 *   <li>30-day payment term — configurable per business</li>
 * </ul>
 *
 * <p><b>Precondition:</b> the event must be published inside an active transaction.
 * AFTER_COMMIT listeners are <b>not</b> invoked for non-transactional publishers
 * (Spring's default {@code fallbackExecution = false}).</p>
 */
@Slf4j
public class DeliveryToReceivableListener {

    private final AccountsReceivableCallback receivableCallback;
    private final int paymentTermDays;

    public DeliveryToReceivableListener(AccountsReceivableCallback receivableCallback) {
        this(receivableCallback, 30);
    }

    public DeliveryToReceivableListener(AccountsReceivableCallback receivableCallback, int paymentTermDays) {
        this.receivableCallback = receivableCallback;
        this.paymentTermDays = paymentTermDays;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("eventAsyncExecutor")
    public void onDeliveryComplete(DeliveryCompleteEvent event) {
        BigDecimal amount = event.getOrderAmount();
        if (amount == null) {
            /* fail-fast：0 元应收是数据错误的遮羞布——宁可拒绝落库也不生成错账 */
            throw new IllegalArgumentException("拒绝创建应收：orderAmount 为 null"
                    + "（deliveryId=" + event.getDeliveryId() + ", orderId=" + event.getOrderId() + "）");
        }
        try {
            /* 账期锚定事件携带的发货日期，而非监听线程的 now()——异步延迟不得使账期漂移 */
            receivableCallback.createReceivable(
                    event.getOrderId(),
                    event.getOrderNo(),
                    event.getCustomerId(),
                    amount,
                    BigDecimal.ZERO,
                    amount,
                    event.getDeliveryDate().plusDays(paymentTermDays),
                    "PENDING"
            );

            log.info("Delivery event → auto-created receivable: deliveryId={}, orderId={}, amount={}",
                    event.getDeliveryId(), event.getOrderId(), amount);
        } catch (Exception e) {
            /* 发后即忘，但绝不静默：带全量业务键落 error 日志，供人工补偿/对账 */
            log.error("创建应收失败，需人工补偿/对账：deliveryId={}, orderId={}, orderNo={}, amount={}, error={}",
                    event.getDeliveryId(), event.getOrderId(), event.getOrderNo(), amount, e.getMessage(), e);
        }
    }

    /**
     * Callback interface for accounts receivable creation.
     * Decouples the listener from specific database/service implementations.
     *
     * <p><b>Idempotency contract:</b> implementations must be idempotent on
     * {@code orderId} (e.g., unique index + existence check, or
     * {@code INSERT ... ON DUPLICATE KEY UPDATE}). The same event may be
     * delivered more than once in production (executor retries, at-least-once
     * infrastructure); the callback owns deduplication.</p>
     */
    public interface AccountsReceivableCallback {
        void createReceivable(Long orderId, String orderNo, Long customerId,
                              BigDecimal amount, BigDecimal receivedAmount,
                              BigDecimal outstandingAmount, LocalDate dueDate,
                              String status);
    }
}
