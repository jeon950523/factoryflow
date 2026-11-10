package com.factoryflow.production.dto;

import com.factoryflow.production.WorkOrderMaterialEntity;

public record WorkOrderMaterialResponse(Long materialItemId, long requiredQty) {
    public static WorkOrderMaterialResponse from(WorkOrderMaterialEntity material) {
        return new WorkOrderMaterialResponse(material.getMaterialItemId(), material.getRequiredQty());
    }
}
