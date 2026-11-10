package com.factoryflow.item;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomRepository extends JpaRepository<BomEntity, Long> {
    boolean existsByFinishedItemId(Long finishedItemId);
    Optional<BomEntity> findByFinishedItemId(Long finishedItemId);
}
