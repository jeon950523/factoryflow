package com.factoryflow.shipment;

import com.factoryflow.shipment.dto.CreateShipmentRequest;
import com.factoryflow.shipment.dto.ShipmentResponse;
import com.factoryflow.shipment.validation.CanonicalShipment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ShipmentService {
    private final ShipmentWriteService writer;
    private final ShipmentLookupService lookup;

    public ShipmentService(ShipmentWriteService writer, ShipmentLookupService lookup) {
        this.writer = writer;
        this.lookup = lookup;
    }

    public ShipmentOutcome ship(CreateShipmentRequest request) {
        CanonicalShipment command = CanonicalShipment.from(request);
        var existing = lookup.findSameOrConflict(command);
        if (existing.isPresent()) return new ShipmentOutcome(existing.get(), false);
        try {
            return new ShipmentOutcome(writer.write(command), true);
        } catch (DataIntegrityViolationException exception) {
            // UNIQUE 경쟁 실패로 종료된 트랜잭션 밖에서 동일 요청인지 재확인한다.
            return new ShipmentOutcome(lookup.findSameOrConflict(command).orElseThrow(() -> exception), false);
        }
    }

    public record ShipmentOutcome(ShipmentResponse response, boolean created) {}
}
