package com.factoryflow.shipment.validation;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.shipment.dto.CreateShipmentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public record CanonicalShipment(String requestKey, Long finishedItemId, long quantity, String payloadHash) {
    public static CanonicalShipment from(CreateShipmentRequest request) {
        if (request == null || request.finishedItemId() == null || request.finishedItemId() <= 0
                || request.quantity() == null || request.quantity() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT", "완제품 ID와 출고 수량이 올바르지 않습니다.");
        }
        String key = request.requestKey() == null ? "" : request.requestKey().strip();
        if (key.isEmpty() || key.length() > 100 || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT_KEY", "출고 요청 키 형식이 올바르지 않습니다.");
        }
        key = key.toUpperCase(Locale.ROOT);
        String text = request.finishedItemId() + ":" + request.quantity();
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
            return new CanonicalShipment(key, request.finishedItemId(), request.quantity(), digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
