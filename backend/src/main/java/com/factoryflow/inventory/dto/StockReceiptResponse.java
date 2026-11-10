package com.factoryflow.inventory.dto;

import com.factoryflow.inventory.StockReceiptEntity;
import java.time.LocalDateTime;

public record StockReceiptResponse(
        Long id,
        String requestKey,
        Long itemId,
        Long inventoryLotId,
        String lotCode,
        long quantity,
        LocalDateTime createdAt
) {
    public static StockReceiptResponse from(StockReceiptEntity receipt, String lotCode) {
        return new StockReceiptResponse(receipt.getId(), receipt.getRequestKey(), receipt.getItemId(),
                receipt.getInventoryLotId(), lotCode, receipt.getQuantity(), receipt.getCreatedAt());
    }
}
