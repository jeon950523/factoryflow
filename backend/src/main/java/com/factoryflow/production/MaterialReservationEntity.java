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
@Table(name = "material_reservations")
public class MaterialReservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "material_item_id", nullable = false)
    private Long materialItemId;

    @Column(name = "inventory_lot_id", nullable = false)
    private Long inventoryLotId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "consumed_at", nullable = true)
    private LocalDateTime consumedAt;

    @Column(name = "released_at", nullable = true)
    private LocalDateTime releasedAt;

    protected MaterialReservationEntity() {
        // Required by JPA.
    }

    public static MaterialReservationEntity reserve(Long orderId, Long materialItemId, Long lotId, long quantity) {
        if (orderId == null || materialItemId == null || lotId == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid reservation");
        }
        MaterialReservationEntity reservation = new MaterialReservationEntity();
        reservation.workOrderId = orderId;
        reservation.materialItemId = materialItemId;
        reservation.inventoryLotId = lotId;
        reservation.quantity = quantity;
        reservation.status = ReservationStatus.RESERVED;
        reservation.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return reservation;
    }

    public void consume() {
        if (status != ReservationStatus.RESERVED) throw new IllegalStateException("Reservation already processed");
        status = ReservationStatus.CONSUMED;
        consumedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public void release() {
        if (status != ReservationStatus.RESERVED) throw new IllegalStateException("Reservation already processed");
        status = ReservationStatus.RELEASED;
        releasedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public Long getWorkOrderId() { return workOrderId; }
    public Long getMaterialItemId() { return materialItemId; }
    public Long getInventoryLotId() { return inventoryLotId; }
    public long getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
}
