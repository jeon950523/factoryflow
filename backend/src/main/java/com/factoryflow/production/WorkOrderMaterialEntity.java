package com.factoryflow.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping baseline: business methods and repositories are added by domain TASKs. */
@Entity
@Table(name = "work_order_materials")
public class WorkOrderMaterialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "material_item_id", nullable = false)
    private Long materialItemId;

    @Column(name = "required_qty", nullable = false)
    private long requiredQty;

    protected WorkOrderMaterialEntity() {
        // JPA requires a no-args constructor.
    }

    public static WorkOrderMaterialEntity snapshot(Long orderId, Long materialItemId, long requiredQty) {
        if (orderId == null || materialItemId == null || requiredQty <= 0) {
            throw new IllegalArgumentException("Invalid work order material snapshot");
        }
        WorkOrderMaterialEntity entity = new WorkOrderMaterialEntity();
        entity.workOrderId = orderId;
        entity.materialItemId = materialItemId;
        entity.requiredQty = requiredQty;
        return entity;
    }

    public Long getWorkOrderId() { return workOrderId; }
    public Long getMaterialItemId() { return materialItemId; }
    public long getRequiredQty() { return requiredQty; }
}
