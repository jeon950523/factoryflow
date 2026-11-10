package com.factoryflow.inventory.dto;

import com.factoryflow.item.ItemType;

public record InventorySummaryResponse(
        Long itemId, String sku, String name, ItemType itemType,
        long onHand, long reserved, long available
) {}
