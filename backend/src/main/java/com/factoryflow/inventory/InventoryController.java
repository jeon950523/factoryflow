package com.factoryflow.inventory;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.global.api.PageResponse;
import com.factoryflow.inventory.dto.InventoryLotResponse;
import com.factoryflow.inventory.dto.InventorySummaryResponse;
import com.factoryflow.item.ItemType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryReadService service;

    public InventoryController(InventoryReadService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<InventorySummaryResponse>> summary(
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(service.summary(itemId, itemType, page, size));
    }

    @GetMapping("/lots")
    public ApiResponse<PageResponse<InventoryLotResponse>> lots(
            @RequestParam Long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(service.lots(itemId, page, size));
    }

    @GetMapping("/lots/{lotId}")
    public ApiResponse<InventoryLotResponse> lot(@PathVariable Long lotId) {
        return ApiResponse.of(service.lot(lotId));
    }
}
