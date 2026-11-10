package com.factoryflow.quality;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.InventoryLotEntity;
import com.factoryflow.inventory.InventoryLotRepository;
import com.factoryflow.inventory.StockMovementEntity;
import com.factoryflow.inventory.StockMovementRepository;
import com.factoryflow.item.ItemEntity;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.item.ItemType;
import com.factoryflow.production.WorkOrderEntity;
import com.factoryflow.production.WorkOrderRepository;
import com.factoryflow.production.WorkOrderStatus;
import com.factoryflow.quality.dto.ConfirmInspectionRequest;
import com.factoryflow.quality.dto.QualityInspectionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityInspectionService {
    private final ItemRepository items;
    private final WorkOrderRepository orders;
    private final QualityInspectionRepository inspections;
    private final InventoryLotRepository lots;
    private final StockMovementRepository movements;

    public QualityInspectionService(ItemRepository items, WorkOrderRepository orders,
                                    QualityInspectionRepository inspections,
                                    InventoryLotRepository lots, StockMovementRepository movements) {
        this.items = items;
        this.orders = orders;
        this.inspections = inspections;
        this.lots = lots;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public QualityInspectionResponse get(Long orderId) {
        validateId(orderId);
        return inspections.findByWorkOrderId(orderId).map(QualityInspectionResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "품질검사 결과가 없습니다."));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QualityInspectionResponse confirm(Long orderId, ConfirmInspectionRequest request) {
        validateId(orderId);
        if (request.passedQty() < 0 || request.failedQty() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QC_QUANTITY", "검사 수량은 음수일 수 없습니다.");
        }

        // 기존 쓰기 명령의 잠금 규칙과 일치: 완제품 Item -> WorkOrder -> LOT.
        // 완제품 ID는 잠금 목적 조회용이며, WorkOrder는 잠금 후 상태를 재확인한다.
        Long finishedItemId = orders.findFinishedItemIdById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다."));
        ItemEntity item = items.findForUpdate(finishedItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "FINISHED_ITEM_NOT_FOUND", "완제품 품목이 없습니다."));
        if (!item.isActive() || item.getItemType() != ItemType.FINISHED_GOOD) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_FINISHED_ITEM", "유효한 완제품이 아닙니다.");
        }
        WorkOrderEntity order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다."));
        if (order.getStatus() != WorkOrderStatus.AWAITING_QC) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "품질검사 대기 상태가 아닙니다.");
        }
        if (!order.getFinishedItemId().equals(item.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "WORK_ORDER_CHANGED", "생산지시의 완제품 정보가 변경되었습니다.");
        }
        try {
            if (Math.addExact(request.passedQty(), request.failedQty()) != order.getPlannedQty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QC_QUANTITY_MISMATCH", "검사 수량 합계가 생산 계획 수량과 다릅니다.");
            }
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QC_QUANTITY_OVERFLOW", "검사 수량 합계가 허용 범위를 초과했습니다.");
        }

        QualityInspectionEntity inspection = inspections.saveAndFlush(
                QualityInspectionEntity.confirm(orderId, request.passedQty(), request.failedQty()));
        if (request.passedQty() > 0) {
            String lotCode = "FG-WO-" + orderId;
            InventoryLotEntity lot = lots.saveAndFlush(
                    InventoryLotEntity.receiveFinishedGood(item.getId(), lotCode, request.passedQty()));
            movements.saveAndFlush(StockMovementEntity.finishedReceipt(item.getId(), lot.getId(),
                    inspection.getId(), request.passedQty()));
        }
        order.markCompleted();
        orders.flush();
        return QualityInspectionResponse.from(inspection);
    }

    private void validateId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID", "올바른 생산지시 ID가 필요합니다.");
        }
    }
}
