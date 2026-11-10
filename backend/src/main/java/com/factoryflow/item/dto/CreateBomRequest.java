package com.factoryflow.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateBomRequest(
        @NotNull @Positive Long finishedItemId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid BomComponentRequest> components
) {}
