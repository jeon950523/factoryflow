package com.factoryflow.shipment.dto;

import com.factoryflow.shipment.ShipmentEntity;
import com.factoryflow.shipment.ShipmentAllocationEntity;
import java.time.LocalDateTime;
import java.util.List;

public record ShipmentResponse(Long id, String requestKey, Long finishedItemId, long quantity,
                               LocalDateTime createdAt, List<AllocationResponse> allocations) {
    public record AllocationResponse(Long lotId, long quantity) {}

    public static ShipmentResponse from(ShipmentEntity entity, List<ShipmentAllocationEntity> rows) {
        return new ShipmentResponse(entity.getId(), entity.getRequestKey(), entity.getFinishedItemId(),
                entity.getQuantity(), entity.getCreatedAt(),
                rows.stream().map(r -> new AllocationResponse(r.getInventoryLotId(), r.getQuantity())).toList());
    }
}
