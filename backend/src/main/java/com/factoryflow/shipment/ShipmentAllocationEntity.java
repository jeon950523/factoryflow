package com.factoryflow.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping baseline: business methods and repositories are added by domain TASKs. */
@Entity
@Table(name = "shipment_allocations")
public class ShipmentAllocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "inventory_lot_id", nullable = false)
    private Long inventoryLotId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    protected ShipmentAllocationEntity() {
        // Required by JPA; do not expose public mutation yet.
    }
}
