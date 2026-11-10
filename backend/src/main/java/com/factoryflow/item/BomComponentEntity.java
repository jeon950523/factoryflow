package com.factoryflow.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bom_components")
public class BomComponentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "raw_item_id", nullable = false)
    private Long rawItemId;

    @Column(name = "qty_per_unit", nullable = false)
    private long qtyPerUnit;

    protected BomComponentEntity() {
        // JPA requires a protected no-args constructor.
    }

    public static BomComponentEntity of(Long bomId, Long rawItemId, long qtyPerUnit) {
        if (bomId == null || bomId <= 0 || rawItemId == null || rawItemId <= 0 || qtyPerUnit <= 0) {
            throw new IllegalArgumentException("Invalid BOM component");
        }
        BomComponentEntity component = new BomComponentEntity();
        component.bomId = bomId;
        component.rawItemId = rawItemId;
        component.qtyPerUnit = qtyPerUnit;
        return component;
    }

    public Long getId() { return id; }
    public Long getBomId() { return bomId; }
    public Long getRawItemId() { return rawItemId; }
    public long getQtyPerUnit() { return qtyPerUnit; }
}
