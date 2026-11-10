package com.factoryflow.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateShipmentRequest(
        @NotBlank String requestKey,
        @NotNull @Positive Long finishedItemId,
        @NotNull @Positive Long quantity
) {}
