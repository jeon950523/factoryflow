package com.factoryflow.production.dto;

import com.factoryflow.production.WorkOrderEntity;
import com.factoryflow.production.WorkOrderStatus;
import java.time.LocalDateTime;

/** 목록에는 BOM 구성품을 포함하지 않아 N+1 조회를 방지한다. */
public record WorkOrderSummaryResponse(Long id, String orderNo, Long finishedItemId, long plannedQty,
                                       WorkOrderStatus status, LocalDateTime createdAt) {
    public static WorkOrderSummaryResponse from(WorkOrderEntity entity) {
        return new WorkOrderSummaryResponse(entity.getId(), entity.getOrderNo(), entity.getFinishedItemId(),
                entity.getPlannedQty(), entity.getStatus(), entity.getCreatedAt());
    }
}
