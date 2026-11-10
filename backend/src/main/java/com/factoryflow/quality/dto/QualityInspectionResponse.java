package com.factoryflow.quality.dto;

import com.factoryflow.quality.QualityInspectionEntity;
import java.time.LocalDateTime;

public record QualityInspectionResponse(Long id, Long workOrderId, long passedQty, long failedQty,
                                        LocalDateTime inspectedAt) {
    public static QualityInspectionResponse from(QualityInspectionEntity inspection) {
        return new QualityInspectionResponse(inspection.getId(), inspection.getWorkOrderId(),
                inspection.getPassedQty(), inspection.getFailedQty(), inspection.getInspectedAt());
    }
}
