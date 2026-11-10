package com.factoryflow.inventory;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InventoryLotRepository extends JpaRepository<InventoryLotEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lot from InventoryLotEntity lot where lot.itemId = :itemId and lot.lotCode = :lotCode")
    Optional<InventoryLotEntity> findForUpdate(@Param("itemId") Long itemId, @Param("lotCode") String lotCode);

    Page<InventoryLotEntity> findByItemId(Long itemId, Pageable pageable);
}
