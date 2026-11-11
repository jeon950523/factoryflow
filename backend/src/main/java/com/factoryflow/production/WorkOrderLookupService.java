package com.factoryflow.production;

import com.factoryflow.global.api.PageResponse;
import com.factoryflow.global.error.ApiException;
import com.factoryflow.production.dto.WorkOrderSummaryResponse;
import com.factoryflow.production.dto.WorkOrderResponse;
import com.factoryflow.production.validation.CanonicalWorkOrder;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOrderLookupService {
    private final WorkOrderRepository orders;
    private final WorkOrderMaterialRepository materials;

    public WorkOrderLookupService(WorkOrderRepository orders, WorkOrderMaterialRepository materials) {
        this.orders = orders;
        this.materials = materials;
    }

    public PageResponse<WorkOrderSummaryResponse> list(WorkOrderStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page는 0 이상, size는 1~100이어야 합니다.");
        }
        var paging = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        Page<WorkOrderEntity> found = status == null ? orders.findAll(paging) : orders.findByStatus(status, paging);
        return PageResponse.from(found.map(WorkOrderSummaryResponse::from));
    }

    public WorkOrderResponse getById(Long id) {
        WorkOrderEntity order = orders.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "생산지시가 없습니다."));
        return response(order);
    }

    public Optional<WorkOrderResponse> findSameOrConflict(CanonicalWorkOrder command) {
        return orders.findByClientRequestId(command.clientRequestId()).map(order -> {
            if (!order.getRequestHash().equals(command.payloadHash())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "이미 다른 생산지시에 사용된 clientRequestId입니다.");
            }
            return response(order);
        });
    }

    private WorkOrderResponse response(WorkOrderEntity order) {
        return WorkOrderResponse.from(order, materials.findByWorkOrderIdOrderByMaterialItemIdAsc(order.getId()));
    }
}
