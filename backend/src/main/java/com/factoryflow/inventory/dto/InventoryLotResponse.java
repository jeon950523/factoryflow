package com.factoryflow.inventory.dto;

import com.factoryflow.inventory.InventoryLotEntity;
import com.factoryflow.inventory.LotSourceType;
import java.time.LocalDateTime;

public record InventoryLotResponse(
        Long id, Long itemId, String lotCode, LotSourceType sourceType,
        long cumulativeInQty, long onHand, long reserved, long available, LocalDateTime receivedAt
) {
    public static InventoryLotResponse from(InventoryLotEntity lot) {
        return new InventoryLotResponse(lot.getId(), lot.getItemId(), lot.getLotCode(), lot.getSourceType(),
                lot.getCumulativeInQty(), lot.getOnHandQty(), lot.getReservedQty(),
                lot.getOnHandQty() - lot.getReservedQty(), lot.getReceivedAt());
    }
}
