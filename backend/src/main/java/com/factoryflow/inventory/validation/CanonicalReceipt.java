package com.factoryflow.inventory.validation;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.dto.CreateStockReceiptRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** 입고 멱등성에 사용하는 정규화 규약. 입력 문자열의 의미가 로케일에 따라 바뀌지 않게 한다. */
public record CanonicalReceipt(String requestKey, Long itemId, String lotCode, long quantity, String payloadHash) {
    public static CanonicalReceipt of(CreateStockReceiptRequest request) {
        if (request == null || request.itemId() == null || request.itemId() <= 0
                || request.quantity() == null || request.quantity() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIPT", "유효하지 않은 입고 요청입니다.");
        }
        String requestKey = normalize(request.requestKey(), 100, "requestKey");
        String lotCode = normalize(request.lotCode(), 80, "lotCode");
        String canonical = request.itemId() + ":" + lotCode.length() + ":" + lotCode + ":" + request.quantity();
        return new CanonicalReceipt(requestKey, request.itemId(), lotCode, request.quantity(), sha256(canonical));
    }

    private static String normalize(String value, int maxLength, String field) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIPT", field + "이(가) 필요합니다.");
        }
        String trimmed = value.strip();
        // Unicode 대문자 변환(예: ß → SS)으로 다른 입력이 같은 코드가 되는 문제를 방지한다.
        if (trimmed.length() < 1 || trimmed.length() > maxLength
                || !trimmed.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIPT", field + "은(는) 영문·숫자 및 ._:- 문자만 허용합니다.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossibleOnJava21) {
            throw new IllegalStateException("Java 21 SHA-256 implementation unavailable", impossibleOnJava21);
        }
    }
}
