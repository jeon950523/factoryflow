package com.factoryflow.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomComponentRepository extends JpaRepository<BomComponentEntity, Long> {
    List<BomComponentEntity> findByBomIdOrderByRawItemIdAsc(Long bomId);
}
