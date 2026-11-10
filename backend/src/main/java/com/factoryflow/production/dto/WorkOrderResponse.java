package com.factoryflow.production.dto;

import com.factoryflow.production.WorkOrderEntity;
import com.factoryflow.production.WorkOrderMaterialEntity;
import com.factoryflow.production.WorkOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderResponse(
        Long id,
        String orderNo,
        String clientRequestId,
        Long finishedItemId,
        Long bomId,
        long plannedQty,
        WorkOrderStatus status,
        LocalDateTime createdAt,
        List<WorkOrderMaterialResponse> materials
) {
    public static WorkOrderResponse from(WorkOrderEntity order, List<WorkOrderMaterialEntity> materials) {
        return new WorkOrderResponse(order.getId(), order.getOrderNo(), order.getClientRequestId(),
                order.getFinishedItemId(), order.getBomId(), order.getPlannedQty(), order.getStatus(),
                order.getCreatedAt(), materials.stream().map(WorkOrderMaterialResponse::from).toList());
    }
}
