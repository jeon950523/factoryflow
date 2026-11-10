package com.factoryflow.production;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.InventoryLotEntity;
import com.factoryflow.inventory.InventoryLotRepository;
import com.factoryflow.inventory.StockMovementEntity;
import com.factoryflow.inventory.StockMovementRepository;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.production.dto.WorkOrderResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 예약 소모와 이동 이력, 생산지시 상태를 하나의 MySQL 트랜잭션에서 확정한다. */
@Service
public class WorkOrderProductionService {
    private final ItemRepository items;
    private final WorkOrderRepository orders;
    private final WorkOrderMaterialRepository materials;
    private final MaterialReservationRepository reservations;
    private final InventoryLotRepository lots;
    private final StockMovementRepository movements;

    public WorkOrderProductionService(ItemRepository items, WorkOrderRepository orders,
                                      WorkOrderMaterialRepository materials,
                                      MaterialReservationRepository reservations,
                                      InventoryLotRepository lots, StockMovementRepository movements) {
        this.items = items;
        this.orders = orders;
        this.materials = materials;
        this.reservations = reservations;
        this.lots = lots;
        this.movements = movements;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkOrderResponse start(Long orderId) {
        List<WorkOrderMaterialEntity> snapshot = materialSnapshot(orderId);
        // 입고·예약·취소의 기존 정책과 일치: Item ID → WorkOrder → LOT.
        snapshot.stream().map(WorkOrderMaterialEntity::getMaterialItemId).distinct().sorted()
                .forEach(id -> items.findForUpdate(id)
                        .orElseThrow(() -> conflict("MATERIAL_NOT_FOUND", "원자재가 없습니다.")));
        WorkOrderEntity order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다."));
        requireStatus(order, WorkOrderStatus.RESERVED);

        List<MaterialReservationEntity> active = reservations
                .findByWorkOrderIdAndStatusOrderByInventoryLotIdAsc(orderId, ReservationStatus.RESERVED);
        verifyCompleteAllocation(snapshot, active);
        for (MaterialReservationEntity reservation : active) {
            InventoryLotEntity lot = lots.findByIdForUpdate(reservation.getInventoryLotId())
                    .orElseThrow(() -> conflict("RESERVATION_INTEGRITY_CONFLICT", "예약 LOT가 없습니다."));
            if (!lot.getItemId().equals(reservation.getMaterialItemId())) {
                throw conflict("RESERVATION_INTEGRITY_CONFLICT", "LOT와 예약의 품목이 다릅니다.");
            }
            lot.consumeReserved(reservation.getQuantity());
            reservation.consume();
            movements.save(StockMovementEntity.rawConsumption(reservation.getMaterialItemId(),
                    lot.getId(), orderId, reservation.getQuantity()));
        }
        movements.flush(); // 제약 충돌 시 모든 LOT 및 상태 변경을 함께 롤백.
        reservations.flush();
        order.markStarted();
        orders.flush();
        return WorkOrderResponse.from(order, snapshot);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkOrderResponse finish(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID", "올바른 생산지시 ID가 필요합니다.");
        }
        WorkOrderEntity order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다."));
        requireStatus(order, WorkOrderStatus.IN_PROGRESS);
        order.markFinished(); // 실제 수량 이동은 없다. 품질검사 전 재고 반영 금지.
        orders.flush();
        return WorkOrderResponse.from(order, materialSnapshot(orderId));
    }

    private void verifyCompleteAllocation(List<WorkOrderMaterialEntity> snapshot,
                                          List<MaterialReservationEntity> active) {
        Map<Long, Long> totals = new HashMap<>();
        for (MaterialReservationEntity reservation : active) {
            try {
                totals.merge(reservation.getMaterialItemId(), reservation.getQuantity(), Math::addExact);
            } catch (ArithmeticException overflow) {
                throw conflict("RESERVATION_INTEGRITY_CONFLICT", "예약 수량 합계가 잘못되었습니다.");
            }
        }
        if (active.isEmpty() || totals.size() != snapshot.size()) {
            throw conflict("RESERVATION_INTEGRITY_CONFLICT", "생산지시의 예약 내역이 불완전합니다.");
        }
        for (WorkOrderMaterialEntity material : snapshot) {
            if (!Long.valueOf(material.getRequiredQty()).equals(totals.get(material.getMaterialItemId()))) {
                throw conflict("RESERVATION_INTEGRITY_CONFLICT", "예약 합계가 BOM 필요량과 다릅니다.");
            }
        }
    }

    private List<WorkOrderMaterialEntity> materialSnapshot(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID", "올바른 생산지시 ID가 필요합니다.");
        }
        if (!orders.existsById(orderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다.");
        }
        List<WorkOrderMaterialEntity> snapshot = materials.findByWorkOrderIdOrderByMaterialItemIdAsc(orderId);
        if (snapshot.isEmpty()) throw conflict("EMPTY_WORK_ORDER_MATERIALS", "생산지시 원자재 정보가 없습니다.");
        return snapshot;
    }

    private void requireStatus(WorkOrderEntity order, WorkOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw conflict("STATE_CONFLICT", "현재 상태에서 수행할 수 없는 생산 명령입니다.");
        }
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
