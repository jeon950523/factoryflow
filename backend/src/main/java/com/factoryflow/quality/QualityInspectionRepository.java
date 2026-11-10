package com.factoryflow.quality;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityInspectionRepository extends JpaRepository<QualityInspectionEntity, Long> {
    Optional<QualityInspectionEntity> findByWorkOrderId(Long workOrderId);
}
