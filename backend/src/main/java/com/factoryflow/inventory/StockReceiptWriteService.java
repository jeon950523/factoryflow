package com.factoryflow.inventory;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.dto.StockReceiptResponse;
import com.factoryflow.inventory.validation.CanonicalReceipt;
import com.factoryflow.item.ItemEntity;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.item.ItemType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReceiptWriteService {
    private final ItemRepository items;
    private final InventoryLotRepository lots;
    private final StockReceiptRepository receipts;
    private final StockMovementRepository movements;

    public StockReceiptWriteService(ItemRepository items, InventoryLotRepository lots,
                                    StockReceiptRepository receipts, StockMovementRepository movements) {
        this.items = items;
        this.lots = lots;
        this.receipts = receipts;
        this.movements = movements;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiptOutcome write(CanonicalReceipt command) {
        // 모든 재고 쓰기 명령은 Item 선잠금 순서를 유지한다.
        ItemEntity item = items.findForUpdate(command.itemId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "품목을 찾을 수 없습니다."));
        if (!item.isActive() || item.getItemType() != ItemType.RAW_MATERIAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RAW_MATERIAL", "활성 원자재 품목만 입고할 수 있습니다.");
        }
        // 같은 품목의 동시 요청은 위 Item 잠금에서 직렬화된다. 락 획득 후 재확인한다.
        var existingReceipt = receipts.findByRequestKey(command.requestKey());
        if (existingReceipt.isPresent()) {
            StockReceiptEntity existing = existingReceipt.get();
            if (!existing.getPayloadHash().equals(command.payloadHash())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "이미 다른 내용으로 사용된 requestKey입니다.");
            }
            InventoryLotEntity existingLot = lots.findById(existing.getInventoryLotId())
                    .orElseThrow(() -> new IllegalStateException("Receipt references missing LOT"));
            return new ReceiptOutcome(StockReceiptResponse.from(existing, existingLot.getLotCode()), false);
        }
        InventoryLotEntity lot = lots.findForUpdate(item.getId(), command.lotCode())
                .map(existing -> {
                    existing.receiveAdditionalRawMaterial(command.quantity());
                    return existing;
                })
                .orElseGet(() -> lots.saveAndFlush(
                        InventoryLotEntity.receiveNewRawMaterial(item.getId(), command.lotCode(), command.quantity())));
        StockReceiptEntity receipt = receipts.saveAndFlush(StockReceiptEntity.of(
                command.requestKey(), command.payloadHash(), item.getId(), lot.getId(), command.quantity()));
        movements.saveAndFlush(StockMovementEntity.rawReceipt(
                item.getId(), lot.getId(), receipt.getId(), command.quantity()));
        return new ReceiptOutcome(StockReceiptResponse.from(receipt, lot.getLotCode()), true);
    }
}
