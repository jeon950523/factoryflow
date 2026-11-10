package com.factoryflow.production;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.production.dto.CreateWorkOrderRequest;
import com.factoryflow.production.dto.WorkOrderResponse;
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
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
    private final WorkOrderService service;
    private final WorkOrderLookupService lookup;

    public WorkOrderController(WorkOrderService service, WorkOrderLookupService lookup) {
        this.service = service;
        this.lookup = lookup;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkOrderResponse>> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrderOutcome outcome = service.create(request);
        if (outcome.created()) {
            return ResponseEntity.created(URI.create("/api/v1/work-orders/" + outcome.response().id()))
                    .body(ApiResponse.of(outcome.response()));
        }
        return ResponseEntity.ok(ApiResponse.of(outcome.response()));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.of(lookup.getById(id));
    }
}
