package com.factoryflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stock_movements")
public class StockMovementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_lot_id", nullable = false)
    private Long inventoryLotId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "movement_type", nullable = false, length = 32)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_type", nullable = false, length = 32)
    private ReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StockMovementEntity() {
    }

    public static StockMovementEntity rawReceipt(Long itemId, Long lotId, Long receiptId, long quantity) {
        StockMovementEntity movement = new StockMovementEntity();
        movement.inventoryLotId = lotId;
        movement.itemId = itemId;
        movement.movementType = MovementType.RAW_RECEIPT;
        movement.quantity = quantity;
        movement.referenceType = ReferenceType.RECEIPT;
        movement.referenceId = receiptId;
        movement.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return movement;
    }

    public static StockMovementEntity rawConsumption(Long itemId, Long lotId, Long workOrderId, long quantity) {
        if (itemId == null || lotId == null || workOrderId == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid raw consumption movement");
        }
        StockMovementEntity movement = new StockMovementEntity();
        movement.inventoryLotId = lotId;
        movement.itemId = itemId;
        movement.movementType = MovementType.RAW_CONSUMPTION;
        movement.quantity = quantity;
        movement.referenceType = ReferenceType.WORK_ORDER;
        movement.referenceId = workOrderId;
        movement.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return movement;
    }

    public Long getId() { return id; }
    public Long getInventoryLotId() { return inventoryLotId; }
    public Long getItemId() { return itemId; }
    public MovementType getMovementType() { return movementType; }
    public long getQuantity() { return quantity; }
    public ReferenceType getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
