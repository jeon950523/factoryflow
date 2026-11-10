package com.factoryflow.global.error;

import java.time.Instant;

public record ApiErrorResponse(
        String errorCode,
        String message,
        String traceId,
        Instant timestamp
) {}
