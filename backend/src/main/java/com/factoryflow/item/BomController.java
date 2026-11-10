package com.factoryflow.item;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.item.dto.BomResponse;
import com.factoryflow.item.dto.CreateBomRequest;
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
@RequestMapping("/api/v1/boms")
public class BomController {
    private final BomService service;

    public BomController(BomService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BomResponse>> create(@Valid @RequestBody CreateBomRequest request) {
        BomResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/boms/" + created.finishedItemId()))
                .body(ApiResponse.of(created));
    }

    @GetMapping("/{finishedItemId}")
    public ApiResponse<BomResponse> get(@PathVariable Long finishedItemId) {
        return ApiResponse.of(service.getByFinishedItemId(finishedItemId));
    }
}
