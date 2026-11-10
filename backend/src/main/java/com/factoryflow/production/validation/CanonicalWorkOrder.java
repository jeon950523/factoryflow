package com.factoryflow.production.validation;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.production.dto.CreateWorkOrderRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public record CanonicalWorkOrder(String clientRequestId, Long finishedItemId, long plannedQty, String payloadHash) {
    public static CanonicalWorkOrder of(CreateWorkOrderRequest request) {
        if (request == null || request.finishedItemId() == null || request.finishedItemId() <= 0
                || request.plannedQty() == null || request.plannedQty() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_ORDER", "유효하지 않은 생산지시 요청입니다.");
        }
        String key = request.clientRequestId() == null ? "" : request.clientRequestId().strip();
        if (key.length() < 1 || key.length() > 100 || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_ID", "clientRequestId 형식이 올바르지 않습니다.");
        }
        String canonicalKey = key.toUpperCase(Locale.ROOT);
        String canonicalPayload = request.finishedItemId() + ":" + request.plannedQty();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
            return new CanonicalWorkOrder(canonicalKey, request.finishedItemId(), request.plannedQty(), hash);
        } catch (NoSuchAlgorithmException impossibleOnJava21) {
            throw new IllegalStateException("SHA-256 not available", impossibleOnJava21);
        }
    }
}
