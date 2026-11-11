package com.factoryflow.shipment.dto;

import com.factoryflow.shipment.ShipmentEntity;
import java.time.LocalDateTime;

/** 출고 목록은 식별·수량만 반환하고 LOT 배분은 상세에서 조회한다. */
public record ShipmentSummaryResponse(Long id, String requestKey, Long finishedItemId,
                                      long quantity, LocalDateTime createdAt) {
    public static ShipmentSummaryResponse from(ShipmentEntity entity) {
        return new ShipmentSummaryResponse(entity.getId(), entity.getRequestKey(), entity.getFinishedItemId(),
                entity.getQuantity(), entity.getCreatedAt());
    }
}
