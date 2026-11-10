package com.factoryflow.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateStockReceiptRequest(
        @NotBlank @Size(max = 100) String requestKey,
        @NotNull @Positive Long itemId,
        @NotBlank @Size(max = 80) String lotCode,
        @NotNull @Positive Long quantity
) {}
