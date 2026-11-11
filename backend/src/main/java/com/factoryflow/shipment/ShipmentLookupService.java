package com.factoryflow.shipment;

import com.factoryflow.global.api.PageResponse;
import com.factoryflow.global.error.ApiException;
import com.factoryflow.shipment.dto.ShipmentSummaryResponse;
import com.factoryflow.shipment.dto.ShipmentResponse;
import com.factoryflow.shipment.validation.CanonicalShipment;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentLookupService {
    private final ShipmentRepository shipments;
    private final ShipmentAllocationRepository allocations;

    public ShipmentLookupService(ShipmentRepository shipments, ShipmentAllocationRepository allocations) {
        this.shipments = shipments;
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public PageResponse<ShipmentSummaryResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page는 0 이상, size는 1~100이어야 합니다.");
        }
        var sorted = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        return PageResponse.from(shipments.findAll(sorted).map(ShipmentSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findById(Long id) {
        if (id == null || id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT_ID", "올바른 출고 ID가 필요합니다.");
        }
        ShipmentEntity entity = shipments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "출고 이력이 없습니다."));
        return ShipmentResponse.from(entity, allocations.findByShipmentIdOrderByInventoryLotIdAsc(id));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ShipmentResponse> findSameOrConflict(CanonicalShipment command) {
        return shipments.findByRequestKey(command.requestKey()).map(entity -> {
            if (!entity.getPayloadHash().equals(command.payloadHash())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "다른 출고 요청에서 사용한 requestKey입니다.");
            }
            return ShipmentResponse.from(entity, allocations.findByShipmentIdOrderByInventoryLotIdAsc(entity.getId()));
        });
    }
}
