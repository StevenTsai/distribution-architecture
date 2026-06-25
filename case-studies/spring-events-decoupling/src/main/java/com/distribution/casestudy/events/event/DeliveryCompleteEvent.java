package com.distribution.casestudy.events.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Delivery completion event — triggers downstream financial processing.
 *
 * <p>Extracted from jewelry ERP {@code DeliveryShippedEvent}. The original event
 * was published by the sales module when a shipment was marked as shipped,
 * and listened to by the finance module to auto-create accounts receivable.</p>
 *
 * <p>Generalization: removed jewelry-specific fields and kept the core
 * delivery-to-finance linkage that applies to any distribution system.</p>
 *
 * <h3>Industry examples</h3>
 * <ul>
 *   <li>Jewelry: shipment of finished jewelry → receivable from distributor</li>
 *   <li>Furniture: delivery of custom furniture → invoice generation</li>
 *   <li>Electronics: shipment of assembled boards → payment schedule</li>
 * </ul>
 */
public class DeliveryCompleteEvent extends ApplicationEvent {

    private final Long deliveryId;
    private final Long orderId;
    private final String orderNo;
    private final Long customerId;
    private final BigDecimal orderAmount;

    public DeliveryCompleteEvent(Object source, Long deliveryId, Long orderId,
                                 String orderNo, Long customerId, BigDecimal orderAmount) {
        super(source);
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.orderAmount = orderAmount;
    }

    public Long getDeliveryId() { return deliveryId; }
    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getOrderAmount() { return orderAmount; }
}
