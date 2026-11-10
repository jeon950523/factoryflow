package com.factoryflow.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReceiptRepository extends JpaRepository<StockReceiptEntity, Long> {
    Optional<StockReceiptEntity> findByRequestKey(String requestKey);
}
