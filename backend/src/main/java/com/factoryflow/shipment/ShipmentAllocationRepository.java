package com.factoryflow.shipment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentAllocationRepository extends JpaRepository<ShipmentAllocationEntity, Long> {
    List<ShipmentAllocationEntity> findByShipmentIdOrderByInventoryLotIdAsc(Long shipmentId);
}
