package com.factoryflow;

import com.factoryflow.item.ItemRepository;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class StockReceiptApiIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_receipts_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @Autowired JdbcTemplate jdbc;

    @Test
    void newAndExistingLotReceiptMustMatchInventoryAndMovements() throws Exception {
        long itemId = createItem("RAW_MATERIAL");
        String lotCode = "LOT-" + random();
        String first = "RECEIPT-" + random();
        String second = "RECEIPT-" + random();
        mvc.perform(postReceipt(first, itemId, lotCode.toLowerCase(), 10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lotCode").value(lotCode))
                .andExpect(jsonPath("$.data.quantity").value(10));
        mvc.perform(postReceipt(second, itemId, lotCode, 5))
                .andExpect(status().isCreated());
        Long receiptId = jdbc.queryForObject(
                "SELECT id FROM stock_receipts WHERE request_key = ?", Long.class, first);
        mvc.perform(get("/api/v1/stock-receipts/{id}", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestKey").value(first));
        mvc.perform(get("/api/v1/inventory").param("itemId", String.valueOf(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].onHand").value(15))
                .andExpect(jsonPath("$.data.content[0].reserved").value(0))
                .andExpect(jsonPath("$.data.content[0].available").value(15));
        mvc.perform(get("/api/v1/inventory/lots").param("itemId", String.valueOf(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].cumulativeInQty").value(15));
        mvc.perform(get("/api/v1/stock-movements").param("itemId", String.valueOf(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].movementType").value("RAW_RECEIPT"));
        assertEquals(15L, jdbc.queryForObject(
                "SELECT on_hand_qty FROM inventory_lots WHERE item_id = ?", Long.class, itemId));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE item_id = ?", Long.class, itemId));
    }

    @Test
    void repeatedKeyMustReturnOriginalResultOrConflictWithoutChangingStock() throws Exception {
        long itemId = createItem("RAW_MATERIAL");
        String key = "R-" + random();
        String lot = "LOT-" + random();
        MvcResult first = mvc.perform(postReceipt(key, itemId, lot, 7))
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(postReceipt(key.toLowerCase(), itemId, lot.toLowerCase(), 7))
                .andExpect(status().isOk()).andReturn();
        assertEquals(first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
        mvc.perform(postReceipt(key, itemId, lot, 9))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));
        assertEquals(7L, jdbc.queryForObject(
                "SELECT on_hand_qty FROM inventory_lots WHERE item_id = ?", Long.class, itemId));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM stock_receipts WHERE item_id = ?",
                Long.class, itemId));
    }

    @Test
    void concurrentIdenticalKeyMustHaveOneCreateOneReplay() throws Exception {
        long itemId = createItem("RAW_MATERIAL");
        String key = "RETRY-" + random();
        String lot = "LOT-" + random();
        int[] statuses = concurrent(() -> mvc.perform(postReceipt(key, itemId, lot, 6))
                .andReturn().getResponse().getStatus(), 2);
        assertEquals(1, count(statuses, 201));
        assertEquals(1, count(statuses, 200));
        assertEquals(6L, jdbc.queryForObject("SELECT on_hand_qty FROM inventory_lots WHERE item_id = ?",
                Long.class, itemId));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE item_id = ?",
                Long.class, itemId));
    }

    @Test
    void concurrentDifferentKeysSameLotMustAddExactlyOnceEach() throws Exception {
        long itemId = createItem("RAW_MATERIAL");
        String lot = "LOT-" + random();
        String[] keys = {"A-" + random(), "B-" + random()};
        int[] statuses = concurrent(new Callable<>() {
            private final java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
            @Override public Integer call() throws Exception {
                return mvc.perform(postReceipt(keys[index.getAndIncrement()], itemId, lot, 11))
                        .andReturn().getResponse().getStatus();
            }
        }, 2);
        assertEquals(2, count(statuses, 201));
        assertEquals(22L, jdbc.queryForObject("SELECT on_hand_qty FROM inventory_lots WHERE item_id = ?",
                Long.class, itemId));
        assertEquals(22L, jdbc.queryForObject("SELECT SUM(quantity) FROM stock_movements WHERE item_id = ?",
                Long.class, itemId));
    }

    @Test
    void overflowMustNotPartiallyChangePreviouslyStoredLot() throws Exception {
        long itemId = createItem("RAW_MATERIAL");
        String lot = "LOT-" + random();
        mvc.perform(postReceipt("MAX-" + random(), itemId, lot, Long.MAX_VALUE))
                .andExpect(status().isCreated());
        mvc.perform(postReceipt("OVER-" + random(), itemId, lot, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("QUANTITY_OVERFLOW"));
        assertEquals(Long.MAX_VALUE, jdbc.queryForObject(
                "SELECT on_hand_qty FROM inventory_lots WHERE item_id = ?", Long.class, itemId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_receipts WHERE item_id = ?", Long.class, itemId));
    }

    @Test
    void invalidReceiptsAndMissingLookupsMustNotChangeStock() throws Exception {
        long finishedId = createItem("FINISHED_GOOD");
        long rawId = createItem("RAW_MATERIAL");
        String lot = "LOT-" + random();
        mvc.perform(postReceipt("K-" + random(), finishedId, lot, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RAW_MATERIAL"));
        mvc.perform(postReceipt("K-" + random(), 999999999L, lot, 1))
                .andExpect(status().isNotFound());
        mvc.perform(postReceipt("K-" + random(), rawId, lot, 0))
                .andExpect(status().isBadRequest());
        mvc.perform(postReceipt("K-" + random(), rawId, lot, -10))
                .andExpect(status().isBadRequest());
        mvc.perform(postReceipt("K-" + random(), rawId, "잘못된 LOT", 1))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/inventory/lots").param("itemId", "999999999"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stock-receipts/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_NOT_FOUND"));
        mvc.perform(get("/api/v1/inventory/lots/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOT_NOT_FOUND"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_lots WHERE item_id = ?",
                Long.class, rawId));
    }

    private long createItem(String type) throws Exception {
        String sku = "RM-" + random();
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"%s","name":"테스트 품목","itemType":"%s"}
                        """.formatted(sku, type))).andExpect(status().isCreated());
        return items.findBySku(sku).orElseThrow().getId();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postReceipt(
            String key, long itemId, String lot, long qty) {
        return post("/api/v1/stock-receipts").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestKey":"%s","itemId":%d,"lotCode":"%s","quantity":%d}
                        """.formatted(key, itemId, lot, qty));
    }

    private int[] concurrent(Callable<Integer> request, int n) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<Integer>> responses = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                responses.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                    return request.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int[] statuses = new int[n];
            for (int i = 0; i < n; i++) statuses[i] = responses.get(i).get(30, TimeUnit.SECONDS);
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(int[] values, int expected) {
        int result = 0;
        for (int value : values) if (value == expected) result++;
        return result;
    }

    private String random() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
}
