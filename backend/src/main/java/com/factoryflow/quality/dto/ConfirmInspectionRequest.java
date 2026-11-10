package com.factoryflow.quality.dto;

import jakarta.validation.constraints.Min;

public record ConfirmInspectionRequest(
        @Min(0) long passedQty,
        @Min(0) long failedQty
) {}
