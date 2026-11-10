package com.factoryflow.production;

import com.factoryflow.production.dto.WorkOrderResponse;

public record WorkOrderOutcome(WorkOrderResponse response, boolean created) {
}
