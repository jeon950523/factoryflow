package com.factoryflow;

import com.factoryflow.item.ItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkOrderReservationIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_reservation_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reserveShouldAllocateFifoLotsAndNotConsumePhysicalStock() throws Exception {
        long sheet = createItem("RAW_MATERIAL");
        long bolts = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, sheet, bolts);
        receive(sheet, "A", 4);
        receive(sheet, "B", 6);
        receive(bolts, "A", 30);
        long id = createOrder(finished, 10);

        mvc.perform(postCommand(id, "reserve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
        assertEquals(10L, queryLong("SELECT SUM(on_hand_qty) FROM inventory_lots WHERE item_id=?", sheet));
        assertEquals(10L, queryLong("SELECT SUM(reserved_qty) FROM inventory_lots WHERE item_id=?", sheet));
        assertEquals(20L, queryLong("SELECT SUM(reserved_qty) FROM inventory_lots WHERE item_id=?", bolts));
        assertEquals(4L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=? AND lot_code=?", sheet, "LOT-A-" + codes.get(sheet)));
        assertEquals(6L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=? AND lot_code=?", sheet, "LOT-B-" + codes.get(sheet)));
        assertEquals(3L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=?", id));
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM stock_movements WHERE reference_type='WORK_ORDER' AND reference_id=?", id));
        mvc.perform(get("/api/v1/work-orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    void shortageOfSecondComponentShouldRollbackAllReservations() throws Exception {
        long first = createItem("RAW_MATERIAL");
        long second = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, first, second);
        receive(first, "A", 10);
        receive(second, "A", 1);
        long id = createOrder(finished, 4); // requires 4 of first and 8 of second

        mvc.perform(postCommand(id, "reserve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        assertEquals(0L, queryLong("SELECT SUM(reserved_qty) FROM inventory_lots WHERE item_id IN (?,?)", first, second));
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=?", id));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?", String.class, id));
    }

    @Test
    void cancelReservedShouldRestoreAvailabilityButPreservePhysicalStockAndHistory() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        receive(raw, "A", 10);
        long id = createOrder(finished, 4); // 8 units
        mvc.perform(postCommand(id, "reserve")).andExpect(status().isOk());
        mvc.perform(postCommand(id, "cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        assertEquals(10L, queryLong("SELECT on_hand_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(0L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='RELEASED'", id));
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='RESERVED'", id));
        mvc.perform(postCommand(id, "cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
        mvc.perform(postCommand(id, "reserve")).andExpect(status().isConflict());
        long another = createOrder(finished, 5);
        mvc.perform(postCommand(another, "reserve")).andExpect(status().isOk());
        assertEquals(10L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));
    }

    @Test
    void cancelDraftAndMissingOrderShouldNotCreateReservations() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        long id = createOrder(finished, 1);
        mvc.perform(postCommand(id, "cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=?", id));
        mvc.perform(postCommand(999999999L, "reserve")).andExpect(status().isNotFound());
        mvc.perform(postCommand(999999999L, "cancel")).andExpect(status().isNotFound());
    }

    @Test
    void twoOrdersCompetingForSameTenUnitsMustNotOverReserve() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        receive(raw, "A", 10);
        long one = createOrder(finished, 4); // 8 required
        long two = createOrder(finished, 4);
        AtomicInteger counter = new AtomicInteger();
        int[] statuses = concurrently(() -> mvc.perform(postCommand(counter.getAndIncrement() == 0 ? one : two, "reserve"))
                .andReturn().getResponse().getStatus(), 2);
        assertEquals(1, count(statuses, 200));
        assertEquals(1, count(statuses, 409));
        assertEquals(8L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(10L, queryLong("SELECT on_hand_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id IN (?,?)", one, two));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM work_orders WHERE id IN (?,?) AND status='RESERVED'", one, two));
    }

    @Test
    void concurrentReserveOfSameOrderMustNotDoubleReserve() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        receive(raw, "A", 20);
        long id = createOrder(finished, 5); // 10 required
        int[] statuses = concurrently(() -> mvc.perform(postCommand(id, "reserve"))
                .andReturn().getResponse().getStatus(), 2);
        assertEquals(1, count(statuses, 200));
        assertEquals(1, count(statuses, 409));
        assertEquals(10L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=?", id));
    }

    @Test
    void concurrentReserveAndCancelMustEndWithNoReservation() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        receive(raw, "A", 12);
        long id = createOrder(finished, 3); // needs 6
        AtomicInteger counter = new AtomicInteger();
        int[] statuses = concurrently(() -> mvc.perform(postCommand(id, counter.getAndIncrement() == 0 ? "reserve" : "cancel"))
                .andReturn().getResponse().getStatus(), 2);
        assertEquals(2, count(statuses, 200) + count(statuses, 409)); // both requests handled (200/409)
        assertTrue(count(statuses, 200) >= 1); // cancel always succeeds
        assertEquals(0L, queryLong("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals("CANCELED", jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?", String.class, id));
    }

    private final java.util.Map<Long, String> codes = new java.util.concurrent.ConcurrentHashMap<>();

    private long createItem(String type) throws Exception {
        String sku = (type.equals("RAW_MATERIAL") ? "RM-" : "FG-") + random();
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"%s\",\"name\":\"테스트 품목\",\"itemType\":\"%s\"}".formatted(sku, type)))
                .andExpect(status().isCreated());
        return items.findBySku(sku).orElseThrow().getId();
    }

    private void createBom(long finished, long first, Long second) throws Exception {
        String components = second == null
                ? "[{\"rawItemId\":%d,\"qtyPerUnit\":2}]".formatted(first)
                : "[{\"rawItemId\":%d,\"qtyPerUnit\":1},{\"rawItemId\":%d,\"qtyPerUnit\":2}]".formatted(first, second);
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"finishedItemId\":%d,\"components\":%s}".formatted(finished, components)))
                .andExpect(status().isCreated());
    }

    private void receive(long itemId, String suffix, long qty) throws Exception {
        String prefix = codes.computeIfAbsent(itemId, ignored -> random());
        String lot = "LOT-" + suffix + "-" + prefix;
        mvc.perform(post("/api/v1/stock-receipts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestKey\":\"RECV-%s\",\"itemId\":%d,\"lotCode\":\"%s\",\"quantity\":%d}"
                        .formatted(random(), itemId, lot, qty)))
                .andExpect(status().isCreated());
    }

    private long createOrder(long finished, long qty) throws Exception {
        String key = "WO-" + random();
        mvc.perform(post("/api/v1/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"%s\",\"finishedItemId\":%d,\"plannedQty\":%d}"
                        .formatted(key, finished, qty)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM work_orders WHERE client_request_id=?", Long.class, key);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postCommand(long id, String command) {
        return post("/api/v1/work-orders/{id}/{command}", id, command);
    }

    private long queryLong(String query, Object... params) {
        Long result = jdbc.queryForObject(query, Long.class, params);
        return result == null ? 0L : result;
    }

    private int[] concurrently(Callable<Integer> request, int numberOfCalls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(numberOfCalls);
        CountDownLatch ready = new CountDownLatch(numberOfCalls);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < numberOfCalls; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timeout");
                    return request.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int[] statuses = new int[numberOfCalls];
            for (int i = 0; i < numberOfCalls; i++) statuses[i] = futures.get(i).get(30, TimeUnit.SECONDS);
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(int[] statuses, int code) {
        int count = 0;
        for (int status : statuses) if (status == code) count++;
        return count;
    }

    private String random() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
}
