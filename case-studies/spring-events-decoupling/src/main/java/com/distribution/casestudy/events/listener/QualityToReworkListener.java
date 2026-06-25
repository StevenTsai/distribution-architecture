package com.distribution.casestudy.events.listener;

import com.distribution.casestudy.events.event.QualityDefectEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * Listens for quality defects and auto-creates a rework work order.
 *
 * <p>In the original jewelry ERP, quality defect handling was done entirely
 * within the quality module — no cross-module event. This case study
 * demonstrates how to introduce an event to decouple quality inspection
 * from production rework.</p>
 *
 * <h3>Why this matters</h3>
 * <p>Without the event, the quality module must know about and directly call
 * production services to create rework orders. With the event:</p>
 * <ul>
 *   <li>Quality module only publishes the defect event — doesn't know who listens</li>
 *   <li>Production module listens and creates rework orders — doesn't know who publishes</li>
 *   <li>New listeners can be added (e.g., defect analytics, supplier notification)</li>
 * </ul>
 */
@Slf4j
public class QualityToReworkListener {

    private final ReworkOrderCallback reworkOrderCallback;

    public QualityToReworkListener(ReworkOrderCallback reworkOrderCallback) {
        this.reworkOrderCallback = reworkOrderCallback;
    }

    @EventListener
    @Async("eventAsyncExecutor")
    public void onQualityDefect(QualityDefectEvent event) {
        try {
            reworkOrderCallback.createReworkOrder(
                    event.getWorkOrderId(),
                    event.getWorkOrderNo(),
                    event.getProcessName(),
                    event.getDefectType(),
                    event.getDefectDescription(),
                    event.getUnqualifiedQuantity(),
                    event.getRecommendedAction()
            );

            log.info("Quality defect → created rework order: workOrderId={}, defect={}, qty={}",
                    event.getWorkOrderId(), event.getDefectType(), event.getUnqualifiedQuantity());
        } catch (Exception e) {
            log.error("Failed to create rework order from defect event: workOrderId={}, error={}",
                    event.getWorkOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Callback interface for rework order creation.
     * Decouples quality module from production module.
     */
    public interface ReworkOrderCallback {
        void createReworkOrder(Long originalWorkOrderId, String originalWorkOrderNo,
                               String processName, String defectType,
                               String defectDescription, int quantity,
                               String recommendedAction);
    }
}
