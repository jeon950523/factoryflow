package com.factoryflow.production;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, Long> {
    Optional<WorkOrderEntity> findByClientRequestId(String clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from WorkOrderEntity o where o.id = :orderId")
    Optional<WorkOrderEntity> findForUpdate(@Param("orderId") Long orderId);
}
