CREATE TABLE items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    unit VARCHAR(8) NOT NULL DEFAULT 'EA',
    active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_items_sku UNIQUE (sku),
    CONSTRAINT chk_item_type CHECK (item_type IN ('RAW_MATERIAL', 'FINISHED_GOOD')),
    CONSTRAINT chk_item_unit CHECK (unit = 'EA')
) ENGINE=InnoDB;

CREATE TABLE boms (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    finished_item_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_boms_finished_item UNIQUE (finished_item_id),
    CONSTRAINT fk_boms_finished_item FOREIGN KEY (finished_item_id) REFERENCES items(id)
) ENGINE=InnoDB;

CREATE TABLE bom_components (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bom_id BIGINT NOT NULL,
    raw_item_id BIGINT NOT NULL,
    qty_per_unit BIGINT NOT NULL,
    CONSTRAINT uk_bom_components UNIQUE (bom_id, raw_item_id),
    CONSTRAINT chk_bom_qty CHECK (qty_per_unit > 0),
    CONSTRAINT fk_bom_components_bom FOREIGN KEY (bom_id) REFERENCES boms(id),
    CONSTRAINT fk_bom_components_item FOREIGN KEY (raw_item_id) REFERENCES items(id)
) ENGINE=InnoDB;

CREATE TABLE inventory_lots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    lot_code VARCHAR(80) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    cumulative_in_qty BIGINT NOT NULL DEFAULT 0,
    on_hand_qty BIGINT NOT NULL DEFAULT 0,
    reserved_qty BIGINT NOT NULL DEFAULT 0,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_inventory_lots_code UNIQUE (item_id, lot_code),
    CONSTRAINT chk_inventory_lot_qty CHECK (
        cumulative_in_qty >= 0 AND on_hand_qty >= 0
        AND reserved_qty >= 0 AND reserved_qty <= on_hand_qty
    ),
    CONSTRAINT chk_inventory_lot_source CHECK (source_type IN ('RAW_RECEIPT', 'QC_PASS')),
    INDEX idx_inventory_lots_fifo (item_id, received_at, id),
    CONSTRAINT fk_inventory_lots_item FOREIGN KEY (item_id) REFERENCES items(id)
) ENGINE=InnoDB;

CREATE TABLE stock_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_key VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    item_id BIGINT NOT NULL,
    inventory_lot_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_stock_receipts_request UNIQUE (request_key),
    CONSTRAINT chk_stock_receipts_qty CHECK (quantity > 0),
    CONSTRAINT fk_stock_receipts_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_stock_receipts_lot FOREIGN KEY (inventory_lot_id) REFERENCES inventory_lots(id)
) ENGINE=InnoDB;

CREATE TABLE work_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(80) NOT NULL,
    client_request_id VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    finished_item_id BIGINT NOT NULL,
    bom_id BIGINT NOT NULL,
    planned_qty BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reserved_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    CONSTRAINT uk_work_orders_no UNIQUE (order_no),
    CONSTRAINT uk_work_orders_request UNIQUE (client_request_id),
    CONSTRAINT chk_work_orders_qty CHECK (planned_qty > 0),
    CONSTRAINT chk_work_orders_status CHECK (
        status IN ('DRAFT', 'RESERVED', 'IN_PROGRESS', 'AWAITING_QC', 'COMPLETED', 'CANCELED')
    ),
    INDEX idx_work_orders_status_created (status, created_at),
    CONSTRAINT fk_work_orders_item FOREIGN KEY (finished_item_id) REFERENCES items(id),
    CONSTRAINT fk_work_orders_bom FOREIGN KEY (bom_id) REFERENCES boms(id)
) ENGINE=InnoDB;

CREATE TABLE work_order_materials (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    material_item_id BIGINT NOT NULL,
    required_qty BIGINT NOT NULL,
    CONSTRAINT uk_work_order_materials UNIQUE (work_order_id, material_item_id),
    CONSTRAINT chk_work_order_materials_qty CHECK (required_qty > 0),
    CONSTRAINT fk_work_order_materials_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_materials_item FOREIGN KEY (material_item_id) REFERENCES items(id)
) ENGINE=InnoDB;

CREATE TABLE material_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    material_item_id BIGINT NOT NULL,
    inventory_lot_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    consumed_at DATETIME(6) NULL,
    released_at DATETIME(6) NULL,
    CONSTRAINT uk_material_reservations UNIQUE (work_order_id, inventory_lot_id),
    CONSTRAINT chk_material_reservation_qty CHECK (quantity > 0),
    CONSTRAINT chk_material_reservation_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
    INDEX idx_material_reservations_order_status (work_order_id, status),
    CONSTRAINT fk_material_reservations_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_material_reservations_item FOREIGN KEY (material_item_id) REFERENCES items(id),
    CONSTRAINT fk_material_reservations_lot FOREIGN KEY (inventory_lot_id) REFERENCES inventory_lots(id)
) ENGINE=InnoDB;

CREATE TABLE quality_inspections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    passed_qty BIGINT NOT NULL,
    failed_qty BIGINT NOT NULL,
    inspected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_quality_inspections_order UNIQUE (work_order_id),
    CONSTRAINT chk_quality_inspections_qty CHECK (passed_qty >= 0 AND failed_qty >= 0),
    CONSTRAINT fk_quality_inspections_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id)
) ENGINE=InnoDB;

CREATE TABLE shipments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_key VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    finished_item_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_shipments_request UNIQUE (request_key),
    CONSTRAINT chk_shipments_qty CHECK (quantity > 0),
    CONSTRAINT fk_shipments_item FOREIGN KEY (finished_item_id) REFERENCES items(id)
) ENGINE=InnoDB;

CREATE TABLE shipment_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    inventory_lot_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT uk_shipment_allocations UNIQUE (shipment_id, inventory_lot_id),
    CONSTRAINT chk_shipment_allocations_qty CHECK (quantity > 0),
    CONSTRAINT fk_shipment_allocations_ship FOREIGN KEY (shipment_id) REFERENCES shipments(id),
    CONSTRAINT fk_shipment_allocations_lot FOREIGN KEY (inventory_lot_id) REFERENCES inventory_lots(id)
) ENGINE=InnoDB;

CREATE TABLE stock_movements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_lot_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_stock_movements_ref UNIQUE (movement_type, reference_type, reference_id, inventory_lot_id),
    CONSTRAINT chk_stock_movements_qty CHECK (quantity > 0),
    CONSTRAINT chk_stock_movements_type CHECK (
        movement_type IN ('RAW_RECEIPT', 'RAW_CONSUMPTION', 'FINISHED_RECEIPT', 'FINISHED_SHIPMENT')
    ),
    CONSTRAINT chk_stock_movements_reference CHECK (
        reference_type IN ('RECEIPT', 'WORK_ORDER', 'INSPECTION', 'SHIPMENT')
    ),
    INDEX idx_stock_movements_item_created (item_id, created_at),
    CONSTRAINT fk_stock_movements_lot FOREIGN KEY (inventory_lot_id) REFERENCES inventory_lots(id),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (item_id) REFERENCES items(id)
) ENGINE=InnoDB;
