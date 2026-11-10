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
        // Required by JPA; do not expose public mutation yet.
    }
}
