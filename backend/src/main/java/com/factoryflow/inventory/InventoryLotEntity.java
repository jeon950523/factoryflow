package com.factoryflow.inventory;

import com.factoryflow.global.error.ApiException;
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
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "inventory_lots")
public class InventoryLotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "lot_code", nullable = false, length = 80)
    private String lotCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 32)
    private LotSourceType sourceType;

    @Column(name = "cumulative_in_qty", nullable = false)
    private long cumulativeInQty;

    @Column(name = "on_hand_qty", nullable = false)
    private long onHandQty;

    @Column(name = "reserved_qty", nullable = false)
    private long reservedQty;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected InventoryLotEntity() {
    }

    public static InventoryLotEntity receiveNewRawMaterial(Long itemId, String lotCode, long quantity) {
        validatePositive(quantity);
        InventoryLotEntity lot = new InventoryLotEntity();
        lot.itemId = itemId;
        lot.lotCode = lotCode;
        lot.sourceType = LotSourceType.RAW_RECEIPT;
        lot.cumulativeInQty = quantity;
        lot.onHandQty = quantity;
        lot.reservedQty = 0L;
        lot.receivedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return lot;
    }

    public void receiveAdditionalRawMaterial(long quantity) {
        validatePositive(quantity);
        if (sourceType != LotSourceType.RAW_RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "LOT_SOURCE_CONFLICT", "원자재 입고 LOT가 아닙니다.");
        }
        try {
            long nextCumulative = Math.addExact(cumulativeInQty, quantity);
            long nextOnHand = Math.addExact(onHandQty, quantity);
            // 변경값을 모두 계산한 뒤 대입하여 오버플로 시 부분 변경을 방지한다.
            cumulativeInQty = nextCumulative;
            onHandQty = nextOnHand;
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY_OVERFLOW", "재고 수량의 허용 범위를 초과했습니다.");
        }
    }

    private static void validatePositive(long quantity) {
        if (quantity <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "입고 수량은 1 이상이어야 합니다.");
        }
    }

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public String getLotCode() { return lotCode; }
    public LotSourceType getSourceType() { return sourceType; }
    public long getCumulativeInQty() { return cumulativeInQty; }
    public long getOnHandQty() { return onHandQty; }
    public long getReservedQty() { return reservedQty; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
