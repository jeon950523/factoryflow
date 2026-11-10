package com.factoryflow.shipment;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "shipments")
public class ShipmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
    @Column(name = "finished_item_id", nullable = false)
    private Long finishedItemId;
    @Column(name = "quantity", nullable = false)
    private long quantity;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ShipmentEntity() {}

    public static ShipmentEntity create(String key, String hash, Long finishedItemId, long quantity) {
        if (key == null || key.isBlank() || hash == null || finishedItemId == null || quantity <= 0)
            throw new IllegalArgumentException("Invalid shipment");
        ShipmentEntity entity = new ShipmentEntity();
        entity.requestKey = key;
        entity.payloadHash = hash;
        entity.finishedItemId = finishedItemId;
        entity.quantity = quantity;
        entity.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return entity;
    }

    public Long getId() { return id; }
    public String getRequestKey() { return requestKey; }
    public String getPayloadHash() { return payloadHash; }
    public Long getFinishedItemId() { return finishedItemId; }
    public long getQuantity() { return quantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
