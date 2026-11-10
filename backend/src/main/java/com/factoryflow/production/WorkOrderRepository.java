package com.factoryflow.production;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, Long> {
    Optional<WorkOrderEntity> findByClientRequestId(String clientRequestId);
}
