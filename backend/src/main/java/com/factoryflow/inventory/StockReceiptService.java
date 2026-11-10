package com.factoryflow.inventory;

import com.factoryflow.inventory.dto.CreateStockReceiptRequest;
import com.factoryflow.inventory.validation.CanonicalReceipt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 트랜잭션 밖에서 UNIQUE 충돌을 처리하는 오케스트레이터. */
@Service
public class StockReceiptService {
    private final StockReceiptWriteService writer;
    private final StockReceiptLookupService lookup;

    public StockReceiptService(StockReceiptWriteService writer, StockReceiptLookupService lookup) {
        this.writer = writer;
        this.lookup = lookup;
    }

    public ReceiptOutcome receive(CreateStockReceiptRequest request) {
        CanonicalReceipt command = CanonicalReceipt.of(request);
        var existing = lookup.findSameOrConflict(command);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return writer.write(command); // 별도 Spring Bean의 프록시에서 commit 또는 rollback
        } catch (DataIntegrityViolationException exception) {
            // 실패한 트랜잭션은 writer가 종료시킨 상태다. 새 트랜잭션에서 requestKey를 확인한다.
            return lookup.findSameOrConflict(command).orElseThrow(() -> exception);
        }
    }
}
