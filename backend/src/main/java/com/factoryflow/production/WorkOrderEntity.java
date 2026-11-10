package com.factoryflow.production;

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

/** JPA mapping baseline: business methods and repositories are added by domain TASKs. */
@Entity
@Table(name = "work_orders")
public class WorkOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 80)
    private String orderNo;

    @Column(name = "client_request_id", nullable = false, length = 100)
    private String clientRequestId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "finished_item_id", nullable = false)
    private Long finishedItemId;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "planned_qty", nullable = false)
    private long plannedQty;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private WorkOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reserved_at", nullable = true)
    private LocalDateTime reservedAt;

    @Column(name = "started_at", nullable = true)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = true)
    private LocalDateTime finishedAt;

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;

    @Column(name = "canceled_at", nullable = true)
    private LocalDateTime canceledAt;

    protected WorkOrderEntity() {
        // JPA requires a no-args constructor.
    }

    public static WorkOrderEntity draft(String orderNo, String clientRequestId, String requestHash,
                                        Long finishedItemId, Long bomId, long plannedQty) {
        if (orderNo == null || orderNo.isBlank() || clientRequestId == null || requestHash == null
                || finishedItemId == null || bomId == null || plannedQty <= 0) {
            throw new IllegalArgumentException("Invalid work order draft");
        }
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.orderNo = orderNo;
        entity.clientRequestId = clientRequestId;
        entity.requestHash = requestHash;
        entity.finishedItemId = finishedItemId;
        entity.bomId = bomId;
        entity.plannedQty = plannedQty;
        entity.status = WorkOrderStatus.DRAFT;
        entity.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return entity;
    }

    public void markReserved() {
        if (status != WorkOrderStatus.DRAFT) throw new IllegalStateException("Only DRAFT orders can be reserved");
        status = WorkOrderStatus.RESERVED;
        reservedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public void markStarted() {
        if (status != WorkOrderStatus.RESERVED) throw new IllegalStateException("Only RESERVED orders can be started");
        status = WorkOrderStatus.IN_PROGRESS;
        startedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public void markFinished() {
        if (status != WorkOrderStatus.IN_PROGRESS) throw new IllegalStateException("Only IN_PROGRESS orders can be finished");
        status = WorkOrderStatus.AWAITING_QC;
        finishedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public void markCompleted() {
        if (status != WorkOrderStatus.AWAITING_QC) {
            throw new IllegalStateException("Only AWAITING_QC orders can be completed");
        }
        status = WorkOrderStatus.COMPLETED;
        completedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public void markCanceled() {
        if (status != WorkOrderStatus.DRAFT && status != WorkOrderStatus.RESERVED) {
            throw new IllegalStateException("Only DRAFT/RESERVED orders can be canceled");
        }
        status = WorkOrderStatus.CANCELED;
        canceledAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getClientRequestId() { return clientRequestId; }
    public String getRequestHash() { return requestHash; }
    public Long getFinishedItemId() { return finishedItemId; }
    public Long getBomId() { return bomId; }
    public long getPlannedQty() { return plannedQty; }
    public WorkOrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
