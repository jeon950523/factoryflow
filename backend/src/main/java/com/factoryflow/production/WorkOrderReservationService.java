package com.factoryflow.production;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.InventoryLotEntity;
import com.factoryflow.inventory.InventoryLotRepository;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.production.dto.WorkOrderResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 쓰기 명령 잠금 순서: Item ID 오름차순 -> WorkOrder -> InventoryLot.
 * 한 트랜잭션에서 모든 BOM 필요량을 확보하거나 전체 롤백한다.
 */
@Service
public class WorkOrderReservationService {
    private final ItemRepository items;
    private final WorkOrderRepository orders;
    private final WorkOrderMaterialRepository materials;
    private final MaterialReservationRepository reservations;
    private final InventoryLotRepository lots;

    public WorkOrderReservationService(ItemRepository items, WorkOrderRepository orders,
                                       WorkOrderMaterialRepository materials,
                                       MaterialReservationRepository reservations, InventoryLotRepository lots) {
        this.items = items;
        this.orders = orders;
        this.materials = materials;
        this.reservations = reservations;
        this.lots = lots;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkOrderResponse reserve(Long orderId) {
        WorkOrderEntity order = lockOrderAfterItems(orderId);
        requireStatus(order, WorkOrderStatus.DRAFT);
        List<WorkOrderMaterialEntity> snapshot = materialSnapshot(orderId);
        List<MaterialReservationEntity> allocated = new ArrayList<>();

        for (WorkOrderMaterialEntity material : snapshot) {
            long missing = material.getRequiredQty();
            for (InventoryLotEntity lot : lots.findAllForReservation(material.getMaterialItemId())) {
                if (missing == 0) break;
                long available = lot.getOnHandQty() - lot.getReservedQty();
                if (available <= 0) continue;
                long quantity = Math.min(available, missing);
                lot.reserve(quantity);
                allocated.add(MaterialReservationEntity.reserve(
                        orderId, material.getMaterialItemId(), lot.getId(), quantity));
                missing -= quantity;
            }
            if (missing != 0) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "원자재가 부족하여 전량 예약할 수 없습니다.");
            }
        }
        reservations.saveAllAndFlush(allocated);
        order.markReserved();
        orders.flush();
        return WorkOrderResponse.from(order, snapshot);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkOrderResponse cancel(Long orderId) {
        WorkOrderEntity order = lockOrderAfterItems(orderId);
        if (order.getStatus() != WorkOrderStatus.DRAFT && order.getStatus() != WorkOrderStatus.RESERVED) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "현재 상태에서는 생산지시를 취소할 수 없습니다.");
        }
        List<WorkOrderMaterialEntity> snapshot = materialSnapshot(orderId);
        if (order.getStatus() == WorkOrderStatus.RESERVED) {
            List<MaterialReservationEntity> active = reservations
                    .findByWorkOrderIdAndStatusOrderByInventoryLotIdAsc(orderId, ReservationStatus.RESERVED);
            if (active.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_INTEGRITY_CONFLICT", "생산지시의 예약 기록을 찾을 수 없습니다.");
            }
            for (MaterialReservationEntity reservation : active) {
                InventoryLotEntity lot = lots.findByIdForUpdate(reservation.getInventoryLotId())
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "RESERVATION_INTEGRITY_CONFLICT",
                                "예약 LOT가 없습니다."));
                if (!lot.getItemId().equals(reservation.getMaterialItemId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_INTEGRITY_CONFLICT", "예약 품목과 LOT가 다릅니다.");
                }
                lot.release(reservation.getQuantity());
                reservation.release();
            }
            reservations.flush();
        }
        order.markCanceled();
        orders.flush();
        return WorkOrderResponse.from(order, snapshot);
    }

    /** 잠금 전 스냅샷은 대상 품목 ID 확인용으로만 사용한다. 잠금 후 상태를 다시 검사한다. */
    private WorkOrderEntity lockOrderAfterItems(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID", "올바른 생산지시 ID가 필요합니다.");
        }
        if (!orders.existsById(orderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다.");
        }
        List<WorkOrderMaterialEntity> snapshot = materialSnapshot(orderId);
        snapshot.stream().map(WorkOrderMaterialEntity::getMaterialItemId).distinct().sorted()
                .forEach(id -> items.findForUpdate(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "MATERIAL_NOT_FOUND", "원자재가 없습니다.")));
        return orders.findForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다."));
    }

    private List<WorkOrderMaterialEntity> materialSnapshot(Long orderId) {
        List<WorkOrderMaterialEntity> snapshot = materials.findByWorkOrderIdOrderByMaterialItemIdAsc(orderId);
        if (snapshot.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPTY_WORK_ORDER_MATERIALS", "생산지시 원자재 정보가 없습니다.");
        }
        return snapshot;
    }

    private void requireStatus(WorkOrderEntity order, WorkOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "현재 상태에서는 원자재를 예약할 수 없습니다.");
        }
    }
}
