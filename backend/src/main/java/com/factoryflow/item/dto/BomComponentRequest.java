package com.factoryflow.item.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BomComponentRequest(
        @NotNull @Positive Long rawItemId,
        @NotNull @Positive @Max(1_000_000) Long qtyPerUnit
) {}
