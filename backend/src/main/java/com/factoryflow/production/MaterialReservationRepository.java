package com.factoryflow.production;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialReservationRepository extends JpaRepository<MaterialReservationEntity, Long> {
    List<MaterialReservationEntity> findByWorkOrderIdAndStatusOrderByInventoryLotIdAsc(
            Long workOrderId, ReservationStatus status);
}
