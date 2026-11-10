package com.factoryflow.inventory;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.dto.StockReceiptResponse;
import com.factoryflow.inventory.validation.CanonicalReceipt;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReceiptLookupService {
    private final StockReceiptRepository receipts;
    private final InventoryLotRepository lots;

    public StockReceiptLookupService(StockReceiptRepository receipts, InventoryLotRepository lots) {
        this.receipts = receipts;
        this.lots = lots;
    }

    /** UNIQUE 충돌로 쓰기 트랜잭션이 끝난 뒤에도 별도의 새 트랜잭션에서 다시 읽을 수 있다. */
    @Transactional(readOnly = true)
    public StockReceiptResponse getById(Long id) {
        if (id == null || id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIPT_ID", "유효하지 않은 입고 ID입니다.");
        }
        StockReceiptEntity receipt = receipts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECEIPT_NOT_FOUND", "입고 내역을 찾을 수 없습니다."));
        InventoryLotEntity lot = lots.findById(receipt.getInventoryLotId())
                .orElseThrow(() -> new IllegalStateException("Receipt references missing LOT"));
        return StockReceiptResponse.from(receipt, lot.getLotCode());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ReceiptOutcome> findSameOrConflict(CanonicalReceipt command) {
        return receipts.findByRequestKey(command.requestKey()).map(existing -> {
            if (!existing.getPayloadHash().equals(command.payloadHash())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "이미 다른 내용으로 사용된 requestKey입니다.");
            }
            InventoryLotEntity lot = lots.findById(existing.getInventoryLotId())
                    .orElseThrow(() -> new IllegalStateException("Receipt references missing LOT"));
            return new ReceiptOutcome(StockReceiptResponse.from(existing, lot.getLotCode()), false);
        });
    }
}
