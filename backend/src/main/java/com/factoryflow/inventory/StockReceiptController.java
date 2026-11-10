package com.factoryflow.inventory;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.inventory.dto.CreateStockReceiptRequest;
import com.factoryflow.inventory.dto.StockReceiptResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock-receipts")
public class StockReceiptController {
    private final StockReceiptService service;
    private final StockReceiptLookupService lookup;

    public StockReceiptController(StockReceiptService service, StockReceiptLookupService lookup) {
        this.service = service;
        this.lookup = lookup;
    }

    @GetMapping("/{id}")
    public ApiResponse<StockReceiptResponse> get(@PathVariable Long id) {
        return ApiResponse.of(lookup.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockReceiptResponse>> receive(
            @Valid @RequestBody CreateStockReceiptRequest request) {
        ReceiptOutcome result = service.receive(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/stock-receipts/" + result.response().id()))
                    .body(ApiResponse.of(result.response()));
        }
        return ResponseEntity.ok(ApiResponse.of(result.response()));
    }
}
