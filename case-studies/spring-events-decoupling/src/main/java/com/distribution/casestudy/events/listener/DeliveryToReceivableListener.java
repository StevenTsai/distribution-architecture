package com.distribution.casestudy.events.listener;

import com.distribution.casestudy.events.event.DeliveryCompleteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

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
 * <h3>Key patterns from the original</h3>
 * <ul>
 *   <li>{@code @EventListener} — annotation-driven event handling</li>
 *   <li>{@code @Async("asyncExecutor")} — fire-and-forget, non-blocking</li>
 *   <li>try/catch — failures are logged, never propagated to publisher</li>
 *   <li>30-day payment term — configurable per business</li>
 * </ul>
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

    @EventListener
    @Async("eventAsyncExecutor")
    public void onDeliveryComplete(DeliveryCompleteEvent event) {
        try {
            BigDecimal amount = event.getOrderAmount() != null ? event.getOrderAmount() : BigDecimal.ZERO;

            receivableCallback.createReceivable(
                    event.getOrderId(),
                    event.getOrderNo(),
                    event.getCustomerId(),
                    amount,
                    BigDecimal.ZERO,
                    amount,
                    LocalDate.now().plusDays(paymentTermDays),
                    "PENDING"
            );

            log.info("Delivery event → auto-created receivable: deliveryId={}, orderId={}, amount={}",
                    event.getDeliveryId(), event.getOrderId(), amount);
        } catch (Exception e) {
            // Fire-and-forget: log but don't propagate
            log.error("Failed to create receivable from delivery event: deliveryId={}, error={}",
                    event.getDeliveryId(), e.getMessage(), e);
        }
    }

    /**
     * Callback interface for accounts receivable creation.
     * Decouples the listener from specific database/service implementations.
     */
    public interface AccountsReceivableCallback {
        void createReceivable(Long orderId, String orderNo, Long customerId,
                              BigDecimal amount, BigDecimal receivedAmount,
                              BigDecimal outstandingAmount, LocalDate dueDate,
                              String status);
    }
}
