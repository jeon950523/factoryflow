package com.factoryflow.production;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderMaterialRepository extends JpaRepository<WorkOrderMaterialEntity, Long> {
    List<WorkOrderMaterialEntity> findByWorkOrderIdOrderByMaterialItemIdAsc(Long workOrderId);
}
