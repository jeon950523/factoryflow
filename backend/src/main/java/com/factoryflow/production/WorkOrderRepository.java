package com.factoryflow.production;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, Long> {
    Optional<WorkOrderEntity> findByClientRequestId(String clientRequestId);
    Page<WorkOrderEntity> findByStatus(WorkOrderStatus status, Pageable pageable);

    // Lock 획득 전에는 엔티티를 영속성 컨텍스트에 적재하지 않는다.
    // 잠금 이후 findForUpdate가 변경된 status를 새로 읽어야 동시 검사 중복을 막는다.
    @Query("select o.finishedItemId from WorkOrderEntity o where o.id = :orderId")
    Optional<Long> findFinishedItemIdById(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from WorkOrderEntity o where o.id = :orderId")
    Optional<WorkOrderEntity> findForUpdate(@Param("orderId") Long orderId);
}
