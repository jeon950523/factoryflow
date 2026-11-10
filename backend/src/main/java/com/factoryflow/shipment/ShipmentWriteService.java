package com.factoryflow.shipment;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.InventoryLotEntity;
import com.factoryflow.inventory.InventoryLotRepository;
import com.factoryflow.inventory.LotSourceType;
import com.factoryflow.inventory.StockMovementEntity;
import com.factoryflow.inventory.StockMovementRepository;
import com.factoryflow.item.ItemEntity;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.item.ItemType;
import com.factoryflow.shipment.dto.ShipmentResponse;
import com.factoryflow.shipment.validation.CanonicalShipment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentWriteService {
    private final ItemRepository items;
    private final InventoryLotRepository lots;
    private final ShipmentRepository shipments;
    private final ShipmentAllocationRepository allocations;
    private final StockMovementRepository movements;

    public ShipmentWriteService(ItemRepository items, InventoryLotRepository lots,
                                ShipmentRepository shipments, ShipmentAllocationRepository allocations,
                                StockMovementRepository movements) {
        this.items = items;
        this.lots = lots;
        this.shipments = shipments;
        this.allocations = allocations;
        this.movements = movements;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShipmentResponse write(CanonicalShipment command) {
        // Item 선잠금: 입고·검사·출고가 같은 완제품 재고를 변경할 때 직렬화한다.
        ItemEntity item = items.findForUpdate(command.finishedItemId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "완제품 품목이 없습니다."));
        if (!item.isActive() || item.getItemType() != ItemType.FINISHED_GOOD) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_FINISHED_ITEM", "출고할 수 있는 완제품이 아닙니다.");
        }

        ShipmentEntity shipment = shipments.saveAndFlush(ShipmentEntity.create(
                command.requestKey(), command.payloadHash(), item.getId(), command.quantity()));
        long remaining = command.quantity();
        List<ShipmentAllocationEntity> assigned = new ArrayList<>();
        List<StockMovementEntity> movementRows = new ArrayList<>();
        for (InventoryLotEntity lot : lots.findAllForReservation(item.getId())) {
            if (remaining == 0) break;
            if (lot.getSourceType() != LotSourceType.QC_PASS) continue;
            long available = lot.getOnHandQty() - lot.getReservedQty();
            if (available <= 0) continue;
            long take = Math.min(available, remaining);
            lot.shipFinished(take);
            assigned.add(ShipmentAllocationEntity.allocate(shipment.getId(), lot.getId(), take));
            movementRows.add(StockMovementEntity.finishedShipment(item.getId(), lot.getId(), shipment.getId(), take));
            remaining -= take;
        }
        if (remaining != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FINISHED_STOCK", "출고 가능한 완제품 재고가 부족합니다.");
        }
        allocations.saveAllAndFlush(assigned);
        movements.saveAllAndFlush(movementRows);
        return ShipmentResponse.from(shipment, assigned);
    }
}
