package com.factoryflow.item.dto;

import com.factoryflow.item.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}") String sku,
        @NotBlank @Size(max = 160) String name,
        @NotNull ItemType itemType
) {}
