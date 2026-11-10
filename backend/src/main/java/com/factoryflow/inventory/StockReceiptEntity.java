package com.factoryflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "stock_receipts")
public class StockReceiptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "inventory_lot_id", nullable = false)
    private Long inventoryLotId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StockReceiptEntity() {
    }

    public static StockReceiptEntity of(String requestKey, String payloadHash,
                                        Long itemId, Long lotId, long quantity) {
        StockReceiptEntity receipt = new StockReceiptEntity();
        receipt.requestKey = requestKey;
        receipt.payloadHash = payloadHash;
        receipt.itemId = itemId;
        receipt.inventoryLotId = lotId;
        receipt.quantity = quantity;
        receipt.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return receipt;
    }

    public Long getId() { return id; }
    public String getRequestKey() { return requestKey; }
    public String getPayloadHash() { return payloadHash; }
    public Long getItemId() { return itemId; }
    public Long getInventoryLotId() { return inventoryLotId; }
    public long getQuantity() { return quantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
