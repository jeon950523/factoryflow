package com.factoryflow.item.dto;

import com.factoryflow.item.ItemEntity;
import com.factoryflow.item.ItemType;
import java.time.LocalDateTime;

public record ItemResponse(Long id, String sku, String name, ItemType itemType,
                           String unit, boolean active, LocalDateTime createdAt) {
    public static ItemResponse from(ItemEntity item) {
        return new ItemResponse(item.getId(), item.getSku(), item.getName(), item.getItemType(),
                item.getUnit(), item.isActive(), item.getCreatedAt());
    }
}
