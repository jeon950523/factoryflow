package com.factoryflow.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** JPA mapping baseline: business methods and repositories are added by domain TASKs. */
@Entity
@Table(name = "shipments")
public class ShipmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    protected ShipmentEntity() {
        // Required by JPA; do not expose public mutation yet.
    }
}
