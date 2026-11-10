package com.factoryflow.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateWorkOrderRequest(
        @NotBlank String clientRequestId,
        @NotNull @Positive Long finishedItemId,
        @NotNull @Positive Long plannedQty
) {
}
