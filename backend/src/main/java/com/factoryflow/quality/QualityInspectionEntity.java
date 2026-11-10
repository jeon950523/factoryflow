package com.factoryflow.quality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** JPA mapping baseline: business methods and repositories are added by domain TASKs. */
@Entity
@Table(name = "quality_inspections")
public class QualityInspectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "passed_qty", nullable = false)
    private long passedQty;

    @Column(name = "failed_qty", nullable = false)
    private long failedQty;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    public static QualityInspectionEntity confirm(Long workOrderId, long passedQty, long failedQty) {
        if (workOrderId == null || passedQty < 0 || failedQty < 0) {
            throw new IllegalArgumentException("Invalid inspection");
        }
        QualityInspectionEntity entity = new QualityInspectionEntity();
        entity.workOrderId = workOrderId;
        entity.passedQty = passedQty;
        entity.failedQty = failedQty;
        entity.inspectedAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return entity;
    }

    public Long getId() { return id; }
    public Long getWorkOrderId() { return workOrderId; }
    public long getPassedQty() { return passedQty; }
    public long getFailedQty() { return failedQty; }
    public LocalDateTime getInspectedAt() { return inspectedAt; }

    protected QualityInspectionEntity() {
        // Required by JPA; do not expose public mutation yet.
    }
}
