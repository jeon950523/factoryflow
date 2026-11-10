package com.factoryflow.inventory;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.global.api.PageResponse;
import com.factoryflow.inventory.dto.StockMovementResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock-movements")
public class StockMovementController {
    private final InventoryReadService service;

    public StockMovementController(InventoryReadService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<StockMovementResponse>> list(
            @RequestParam Long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(service.movements(itemId, page, size));
    }
}
