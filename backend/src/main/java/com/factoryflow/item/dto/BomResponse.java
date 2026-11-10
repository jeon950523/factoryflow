package com.factoryflow.item.dto;

import com.factoryflow.item.BomComponentEntity;
import com.factoryflow.item.BomEntity;
import java.time.LocalDateTime;
import java.util.List;

public record BomResponse(Long id, Long finishedItemId, List<Component> components, LocalDateTime createdAt) {
    public record Component(Long rawItemId, long qtyPerUnit) {
        public static Component from(BomComponentEntity entity) {
            return new Component(entity.getRawItemId(), entity.getQtyPerUnit());
        }
    }

    public static BomResponse from(BomEntity bom, List<BomComponentEntity> components) {
        return new BomResponse(bom.getId(), bom.getFinishedItemId(),
                components.stream().map(Component::from).toList(), bom.getCreatedAt());
    }
}
