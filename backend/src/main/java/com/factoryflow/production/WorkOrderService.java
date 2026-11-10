package com.factoryflow.production;

import com.factoryflow.production.dto.CreateWorkOrderRequest;
import com.factoryflow.production.dto.WorkOrderResponse;
import com.factoryflow.production.validation.CanonicalWorkOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 멱등성 충돌 후 새 트랜잭션 조회가 가능하도록 쓰기 Bean과 분리한다. */
@Service
public class WorkOrderService {
    private final WorkOrderWriteService writer;
    private final WorkOrderLookupService lookup;

    public WorkOrderService(WorkOrderWriteService writer, WorkOrderLookupService lookup) {
        this.writer = writer;
        this.lookup = lookup;
    }

    public WorkOrderOutcome create(CreateWorkOrderRequest request) {
        CanonicalWorkOrder command = CanonicalWorkOrder.of(request);
        var existing = lookup.findSameOrConflict(command);
        if (existing.isPresent()) return new WorkOrderOutcome(existing.get(), false);
        try {
            return new WorkOrderOutcome(writer.write(command), true);
        } catch (DataIntegrityViolationException exception) {
            return new WorkOrderOutcome(lookup.findSameOrConflict(command).orElseThrow(() -> exception), false);
        }
    }
}
