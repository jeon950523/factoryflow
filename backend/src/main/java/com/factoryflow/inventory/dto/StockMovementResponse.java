package com.factoryflow.inventory.dto;

import com.factoryflow.inventory.MovementType;
import com.factoryflow.inventory.ReferenceType;
import com.factoryflow.inventory.StockMovementEntity;
import java.time.LocalDateTime;

public record StockMovementResponse(
        Long id, Long itemId, Long inventoryLotId, MovementType movementType,
        long quantity, ReferenceType referenceType, Long referenceId, LocalDateTime createdAt
) {
    public static StockMovementResponse from(StockMovementEntity movement) {
        return new StockMovementResponse(movement.getId(), movement.getItemId(), movement.getInventoryLotId(),
                movement.getMovementType(), movement.getQuantity(), movement.getReferenceType(),
                movement.getReferenceId(), movement.getCreatedAt());
    }
}
