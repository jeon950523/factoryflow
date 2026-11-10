package com.factoryflow.shipment;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.shipment.dto.CreateShipmentRequest;
import com.factoryflow.shipment.dto.ShipmentResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {
    private final ShipmentService service;
    private final ShipmentLookupService lookup;

    public ShipmentController(ShipmentService service, ShipmentLookupService lookup) {
        this.service = service;
        this.lookup = lookup;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentResponse>> create(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentService.ShipmentOutcome outcome = service.ship(request);
        if (outcome.created()) {
            return ResponseEntity.created(URI.create("/api/v1/shipments/" + outcome.response().id()))
                    .body(ApiResponse.of(outcome.response()));
        }
        return ResponseEntity.ok(ApiResponse.of(outcome.response()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShipmentResponse> get(@PathVariable Long id) {
        return ApiResponse.of(lookup.findById(id));
    }
}
