package com.factoryflow.shipment;

import jakarta.persistence.*;

@Entity
@Table(name = "shipment_allocations")
public class ShipmentAllocationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;
    @Column(name = "inventory_lot_id", nullable = false)
    private Long inventoryLotId;
    @Column(name = "quantity", nullable = false)
    private long quantity;

    protected ShipmentAllocationEntity() {}

    public static ShipmentAllocationEntity allocate(Long shipmentId, Long lotId, long quantity) {
        if (shipmentId == null || lotId == null || quantity <= 0)
            throw new IllegalArgumentException("Invalid shipment allocation");
        ShipmentAllocationEntity entity = new ShipmentAllocationEntity();
        entity.shipmentId = shipmentId;
        entity.inventoryLotId = lotId;
        entity.quantity = quantity;
        return entity;
    }

    public Long getId() { return id; }
    public Long getShipmentId() { return shipmentId; }
    public Long getInventoryLotId() { return inventoryLotId; }
    public long getQuantity() { return quantity; }
}
