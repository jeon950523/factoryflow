package com.factoryflow.quality;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.quality.dto.ConfirmInspectionRequest;
import com.factoryflow.quality.dto.QualityInspectionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/inspection")
public class QualityInspectionController {
    private final QualityInspectionService service;

    public QualityInspectionController(QualityInspectionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QualityInspectionResponse>> confirm(
            @PathVariable Long workOrderId, @Valid @RequestBody ConfirmInspectionRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.confirm(workOrderId, request)));
    }

    @GetMapping
    public ApiResponse<QualityInspectionResponse> get(@PathVariable Long workOrderId) {
        return ApiResponse.of(service.get(workOrderId));
    }
}
