package com.distribution.casestudy.events.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;

/**
 * Work order completion event — triggers finished goods inventory creation.
 *
 * <p>In the original jewelry ERP, this was a <strong>direct service call</strong>
 * ({@code finishedInventoryService.createInventory(...)} inside {@code finishWork()}).
 * The production module directly depended on the inventory module.</p>
 *
 * <p>This case study refactors that into an event-driven pattern:
 * the production module publishes this event, and the inventory module
 * listens to create finished goods records. The modules are now decoupled.</p>
 *
 * <h3>Industry examples</h3>
 * <ul>
 *   <li>Jewelry: work order finish → finished jewelry inventory record</li>
 *   <li>Furniture: assembly complete → warehouse receiving record</li>
 *   <li>Electronics: production batch done → finished goods stock entry</li>
 * </ul>
 */
public class WorkOrderFinishedEvent extends ApplicationEvent {

    private final Long workOrderId;
    private final String workOrderNo;
    private final Long productId;
    private final String productCode;
    private final int finishedQuantity;
    private final int defectQuantity;
    private final LocalDate finishedDate;

    public WorkOrderFinishedEvent(Object source, Long workOrderId, String workOrderNo,
                                  Long productId, String productCode,
                                  int finishedQuantity, int defectQuantity,
                                  LocalDate finishedDate) {
        super(source);
        this.workOrderId = workOrderId;
        this.workOrderNo = workOrderNo;
        this.productId = productId;
        this.productCode = productCode;
        this.finishedQuantity = finishedQuantity;
        this.defectQuantity = defectQuantity;
        this.finishedDate = finishedDate;
    }

    public Long getWorkOrderId() { return workOrderId; }
    public String getWorkOrderNo() { return workOrderNo; }
    public Long getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public int getFinishedQuantity() { return finishedQuantity; }
    public int getDefectQuantity() { return defectQuantity; }
    public LocalDate getFinishedDate() { return finishedDate; }
}
