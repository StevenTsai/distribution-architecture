package com.distribution.casestudy.events.event;

import org.springframework.context.ApplicationEvent;

/**
 * Quality defect event — triggers rework work order creation.
 *
 * <p>In the original jewelry ERP, quality defect handling was done via
 * <strong>direct service calls</strong> within the quality module. There was
 * no cross-module event. This case study demonstrates how to decouple
 * quality inspection from production rework by introducing an event.</p>
 *
 * <h3>Industry examples</h3>
 * <ul>
 *   <li>Jewelry: QC reject on stone setting → rework order for re-setting</li>
 *   <li>Furniture: sanding defect detected → rework order for re-finishing</li>
 *   <li>Electronics: AOI failure → rework order for component replacement</li>
 * </ul>
 */
public class QualityDefectEvent extends ApplicationEvent {

    private final Long inspectionId;
    private final Long workOrderId;
    private final String workOrderNo;
    private final String processName;
    private final String defectType;
    private final String defectDescription;
    private final int unqualifiedQuantity;
    private final String recommendedAction;

    public QualityDefectEvent(Object source, Long inspectionId, Long workOrderId,
                              String workOrderNo, String processName,
                              String defectType, String defectDescription,
                              int unqualifiedQuantity, String recommendedAction) {
        super(source);
        this.inspectionId = inspectionId;
        this.workOrderId = workOrderId;
        this.workOrderNo = workOrderNo;
        this.processName = processName;
        this.defectType = defectType;
        this.defectDescription = defectDescription;
        this.unqualifiedQuantity = unqualifiedQuantity;
        this.recommendedAction = recommendedAction;
    }

    public Long getInspectionId() { return inspectionId; }
    public Long getWorkOrderId() { return workOrderId; }
    public String getWorkOrderNo() { return workOrderNo; }
    public String getProcessName() { return processName; }
    public String getDefectType() { return defectType; }
    public String getDefectDescription() { return defectDescription; }
    public int getUnqualifiedQuantity() { return unqualifiedQuantity; }
    public String getRecommendedAction() { return recommendedAction; }
}
