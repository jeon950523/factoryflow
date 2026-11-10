package com.factoryflow.item;

import java.util.Locale;

/** A single normalization rule is used for writes and duplicate checks. */
public final class Sku {
    private Sku() {}

    public static String normalize(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("SKU must be 2-64 ASCII letters, digits, '_' or '-'");
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
