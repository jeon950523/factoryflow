package com.factoryflow.item;

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
@Table(name = "boms")
public class BomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "finished_item_id", nullable = false)
    private Long finishedItemId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BomEntity() {
        // JPA requires a protected no-args constructor.
    }

    public static BomEntity forFinishedItem(Long finishedItemId) {
        if (finishedItemId == null || finishedItemId <= 0) {
            throw new IllegalArgumentException("Finished item ID must be positive");
        }
        BomEntity bom = new BomEntity();
        bom.finishedItemId = finishedItemId;
        bom.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return bom;
    }

    public Long getId() { return id; }
    public Long getFinishedItemId() { return finishedItemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
