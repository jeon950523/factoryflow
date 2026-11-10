package com.factoryflow.inventory;

import com.factoryflow.inventory.dto.StockReceiptResponse;

public record ReceiptOutcome(StockReceiptResponse response, boolean created) {}
