package com.factoryflow.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkuTest {
    @Test
    void canonicalizationUsesRootLocale() {
        assertEquals("RM-SHEET", Sku.normalize("rm-sheet"));
        assertEquals("FG_12", Sku.normalize("Fg_12"));
    }

    @Test
    void invalidCodesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Sku.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> Sku.normalize("한글"));
        assertThrows(IllegalArgumentException.class, () -> Sku.normalize("rm a"));
    }
}
